package dev.structtags.ide;

import com.goide.psi.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import dev.structtags.core.FieldType;
import dev.structtags.core.TagParser;
import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

final class GoFieldTypes {
    private GoFieldTypes() {}
    static FieldType of(GoFieldDeclaration field) {
        return of(field.getType(), field);
    }
    static FieldType of(GoType type, PsiElement context) { return resolve(type, context, Collections.newSetFromMap(new IdentityHashMap<>()), 0); }
    private static FieldType resolve(GoType original, PsiElement context, Set<GoType> seen, int depth) {
        ProgressManager.checkCanceled();
        if (original == null || depth > 24 || !seen.add(original)) return FieldType.UNKNOWN;
        try {
            if (original instanceof GoPointerType pointer)
                return FieldType.container(POINTER, resolve(pointer.getType(), context, seen, depth + 1), null);
            if (original instanceof GoArrayOrSliceType collection)
                return FieldType.container(collection.isArray() ? ARRAY : SLICE, resolve(collection.getType(), context, seen, depth + 1), null);
            if (original instanceof GoMapType map)
                return FieldType.container(MAP, resolve(map.getValueType(), context, seen, depth + 1), resolve(map.getKeyType(), context, seen, depth + 1));
            if (original instanceof GoStructType) return FieldType.of(STRUCT, "struct");
            if (original instanceof GoInterfaceType) return FieldType.of(INTERFACE, "interface");
            if (original instanceof GoSpecType spec) return resolve(spec.getType(), context, seen, depth + 1);
            String text = original.getText().trim();
            if (text.startsWith("(")) {
                GoType inner = PsiTreeUtil.getChildOfType(original, GoType.class);
                if (inner != null) return resolve(inner, context, seen, depth + 1);
            }
            if (text.startsWith("func(" ) || text.startsWith("func (")) return FieldType.of(FUNCTION, "func");
            if (text.startsWith("chan ") || text.startsWith("chan<-") || text.startsWith("<-chan")) return FieldType.of(CHANNEL, "chan");
            GoTypeSpec definition = null;
            if (original.getContainingFile() instanceof GoFile file && text.matches("[A-Za-z_][A-Za-z_0-9]*")) {
                definition = file.getTypes(text).stream().findFirst().orElse(null);
            }
            GoTypeReferenceExpression reference = original.getTypeReferenceExpression();
            if (definition == null && reference != null && !DumbService.isDumb(context.getProject())) {
                PsiElement target = reference.getReference().resolve();
                if (target instanceof GoTypeSpec spec) definition = spec;
            }
            if (definition != null && com.goide.psi.impl.GoPsiImplUtil.isBuiltinFile(definition.getContainingFile())) definition = null;
            if (definition != null) {
                FieldType base = resolve(definition.getSpecType(), context, seen, depth + 1);
                Set<String> methods = new HashSet<>(base.methods());
                // Local method declarations are available even before project indexes finish.
                if (definition.getContainingFile() instanceof GoFile file) {
                    String name = definition.getName();
                    for (GoMethodDeclaration method : file.getMethods()) {
                        String receiver = method.getReceiverType() == null ? "" : method.getReceiverType().getText().replace("*", "").replace("(", "").replace(")", "").trim();
                        if (receiver.equals(name) && method.getName() != null) methods.add(method.getName());
                    }
                }
                if (!DumbService.isDumb(context.getProject())) {
                    for (var method : definition.getAllMethods()) if (method.getName() != null) methods.add(method.getName());
                }
                // Preserve canonical time identity for the DynamoDB convertible-to-time rule.
                String canonical = canonical(text, original.getContainingFile());
                if (canonical.equals("time.Time")) base = FieldType.of(STRUCT, canonical);
                else if (base.kind() == STRUCT && !base.name().equals("time.Time") && !definition.isTypeAlias() && definition.getName() != null) base = FieldType.of(STRUCT, definition.getName());
                return base.withMethods(methods);
            }
            String canonical = canonical(text, original.getContainingFile());
            if (Set.of("time.Time", "net/url.URL", "net/http.Cookie").contains(canonical)) return FieldType.of(STRUCT, canonical);
            if (canonical.equals("net.IP")) return new FieldType(SLICE, canonical, FieldType.of(UNSIGNED, "uint8"), null, Set.of("UnmarshalText", "MarshalText"));
            return switch (text) {
                case "string" -> FieldType.of(STRING, text);
                case "bool" -> FieldType.of(BOOLEAN, text);
                case "int", "int8", "int16", "int32", "int64", "rune" -> FieldType.of(INTEGER, text.equals("rune") ? "int32" : text);
                case "uint", "uint8", "uint16", "uint32", "uint64", "uintptr", "byte" -> FieldType.of(UNSIGNED, text.equals("byte") ? "uint8" : text);
                case "float32", "float64" -> FieldType.of(FLOAT, text);
                case "complex64", "complex128" -> FieldType.of(COMPLEX, text);
                case "any", "error" -> FieldType.of(INTERFACE, text);
                default -> FieldType.UNKNOWN;
            };
        } finally { seen.remove(original); }
    }
    private static String canonical(String text, PsiFile psiFile) {
        if (!(psiFile instanceof GoFile file)) return text;
        int dot = text.indexOf('.');
        String qualifier = dot < 0 ? "" : text.substring(0, dot);
        String name = dot < 0 ? text : text.substring(dot + 1);
        for (GoImportSpec spec : file.getImports()) {
            String path = spec.getPath();
            String alias = spec.getAlias();
            if (path == null || spec.isForSideEffects()) continue;
            String importedName = alias == null || alias.isEmpty() ? path.substring(path.lastIndexOf('/') + 1) : alias;
            if (dot >= 0 && importedName.equals(qualifier) || dot < 0 && spec.isDot()) return path + "." + name;
        }
        return text;
    }
    static boolean humaInput(GoFieldDeclaration field, boolean assumeSharedTags) {
        if (field.getAnonymousFieldDefinition() != null || field.getFieldDefinitionList().stream().anyMatch(f -> Set.of("Body", "RawBody").contains(f.getName()))) return false;
        GoStructType owner = PsiTreeUtil.getParentOfType(field, GoStructType.class);
        if (owner == null) return false;
        if (assumeSharedTags) return true;
        boolean imported = field.getContainingFile() instanceof GoFile file && file.getImports().stream()
            .anyMatch(i -> i.getPath() != null && (i.getPath().equals("github.com/danielgtaylor/huma/v2") || i.getPath().startsWith("github.com/danielgtaylor/huma/v2/")));
        for (GoFieldDeclaration sibling : owner.getFieldDeclarationList()) {
            if (sibling.getTag() == null) continue;
            for (var entry : TagParser.parse(sibling.getTag().getText()).entries()) {
                if (!entry.value().isEmpty() && (Set.of("query", "path", "cookie").contains(entry.key()) || imported && entry.key().equals("form"))) return true;
            }
        }
        if (!imported) return false;
        // A header-only struct may be a response. Require a function input use for that case.
        if (PsiTreeUtil.getParentOfType(owner, GoParameterDeclaration.class) != null) return true;
        GoTypeSpec named = PsiTreeUtil.getParentOfType(owner, GoTypeSpec.class);
        if (named != null) {
            for (GoParameterDeclaration parameter : PsiTreeUtil.findChildrenOfType(field.getContainingFile(), GoParameterDeclaration.class)) {
                GoType type = parameter.getType();
                if (type != null && type.getText().replace("*", "").trim().equals(named.getName())) return true;
            }
        }
        return false;
    }
}

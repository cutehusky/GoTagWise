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
    private record Binding(GoType type, Map<GoTypeParamDefinition, Binding> environment) {}
    static FieldType of(GoType type, PsiElement context) { return resolve(type, context, Collections.newSetFromMap(new IdentityHashMap<>()), 0, Map.of(), true); }
    private static FieldType resolve(GoType original, PsiElement context, Set<GoType> seen, int depth, Map<GoTypeParamDefinition, Binding> environment, boolean unwrap) {
        ProgressManager.checkCanceled();
        if (original == null || depth > 24 || !seen.add(original)) return FieldType.UNKNOWN;
        try {
            if (original instanceof GoPointerType pointer)
                return FieldType.container(POINTER, resolve(pointer.getType(), context, seen, depth + 1, environment, unwrap), null);
            if (original instanceof GoArrayOrSliceType collection)
                return FieldType.container(collection.isArray() ? ARRAY : SLICE, resolve(collection.getType(), context, seen, depth + 1, environment, unwrap), null);
            if (original instanceof GoMapType map)
                return FieldType.container(MAP, resolve(map.getValueType(), context, seen, depth + 1, environment, unwrap), resolve(map.getKeyType(), context, seen, depth + 1, environment, unwrap));
            if (original instanceof GoStructType) return FieldType.of(STRUCT, "struct");
            if (original instanceof GoInterfaceType) return FieldType.of(INTERFACE, "interface");
            if (original instanceof GoSpecType spec) return resolve(spec.getType(), context, seen, depth + 1, environment, unwrap);
            String text = original.getText().trim();
            if (text.startsWith("(")) {
                GoType inner = PsiTreeUtil.getChildOfType(original, GoType.class);
                if (inner != null) return resolve(inner, context, seen, depth + 1, environment, unwrap);
            }
            if (text.startsWith("func(" ) || text.startsWith("func (")) return FieldType.of(FUNCTION, "func");
            if (text.startsWith("chan ") || text.startsWith("chan<-") || text.startsWith("<-chan")) return FieldType.of(CHANNEL, "chan");
            GoTypeReferenceExpression reference = original.getTypeReferenceExpression();
            Binding parameter = parameterBinding(original, environment);
            if (parameter != null) return resolve(parameter.type(), context, seen, depth + 1, parameter.environment(), unwrap);
            GoTypeSpec definition = null;
            if (original.getContainingFile() instanceof GoFile file && reference != null && reference.getQualifier() == null && reference.getIdentifier() != null) {
                definition = file.getTypes(reference.getIdentifier().getText()).stream().findFirst().orElse(null);
            }
            if (definition == null && reference != null && !DumbService.isDumb(context.getProject())) {
                PsiElement target = reference.getReference().resolve();
                if (target instanceof GoTypeSpec spec) definition = spec;
            }
            if (definition != null && com.goide.psi.impl.GoPsiImplUtil.isBuiltinFile(definition.getContainingFile())) definition = null;
            if (definition != null) {
                var parameters = parameters(definition);
                GoTypeArguments arguments = original.getTypeArguments();
                Map<GoTypeParamDefinition, Binding> applied = environment;
                boolean instantiated = !parameters.isEmpty() && arguments != null && arguments.getTypes().size() == parameters.size();
                if (instantiated) {
                    applied = new HashMap<>(environment);
                    for (int i = 0; i < parameters.size(); i++) applied.put(parameters.get(i), new Binding(arguments.getTypes().get(i), environment));
                    applied = Map.copyOf(applied);
                }
                GoTypeParamDefinition payload = unwrap ? payloadParameter(definition, parameters) : null;
                if (payload != null && !instantiated) return FieldType.UNKNOWN;
                FieldType base;
                if (payload != null) {
                    Binding argument = applied.get(payload);
                    base = resolve(argument.type(), context, seen, depth + 1, argument.environment(), true).asGenericValueWrapper();
                } else {
                    // A new non-generic defined struct is not a transparent wrapper; aliases remain transparent.
                    boolean unwrapUnderlying = unwrap && (definition.isTypeAlias() || !parameters.isEmpty());
                    base = resolve(definition.getSpecType(), context, seen, depth + 1, applied, unwrapUnderlying);
                }
                Set<String> methods = new HashSet<>(base.methods());
                // Local method declarations are available even before project indexes finish.
                if (definition.getContainingFile() instanceof GoFile file) {
                    String name = definition.getName();
                    for (GoMethodDeclaration method : file.getMethods()) {
                        String receiver = method.getReceiverType() == null ? "" : method.getReceiverType().getText().replace("*", "").replace("(", "").replace(")", "").trim();
                        if (receiver.split("\\[", 2)[0].equals(name) && method.getName() != null) methods.add(method.getName());
                    }
                }
                if (!DumbService.isDumb(context.getProject())) {
                    for (var method : definition.getAllMethods()) if (method.getName() != null) methods.add(method.getName());
                }
                // Preserve canonical time identity for the DynamoDB convertible-to-time rule.
                String canonical = canonical(text, original.getContainingFile());
                if (canonical.equals("time.Time")) base = FieldType.of(STRUCT, canonical);
                else if (base.kind() == STRUCT && !base.genericValueWrapper() && !base.name().equals("time.Time") && !definition.isTypeAlias() && definition.getName() != null) base = FieldType.of(STRUCT, definition.getName());
                if (payload == null && !definition.isTypeAlias()) base = base.withEnvParser("");
                if (Set.of("time.Duration", "net/url.URL", "time.Location").contains(canonical)) base = base.withEnvParser(canonical);
                return base.withMethods(methods);
            }
            String canonical = canonical(text, original.getContainingFile());
            if (Set.of("time.Time", "net/url.URL", "net/http.Cookie", "time.Location").contains(canonical)) return FieldType.of(STRUCT, canonical).withEnvParser(canonical);
            if (canonical.equals("time.Duration")) return FieldType.of(INTEGER, "int64").withEnvParser(canonical);
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
    private static List<GoTypeParamDefinition> parameters(GoTypeSpec definition) {
        GoTypeParameters parameters = definition.getSpecType().getTypeParameters();
        if (parameters == null) return List.of();
        return parameters.getTypeParameterDeclarationList().stream().flatMap(p -> p.getTypeParamDefinitionList().stream()).toList();
    }
    private static GoTypeParamDefinition payloadParameter(GoTypeSpec definition, List<GoTypeParamDefinition> parameters) {
        if (!(definition.getSpecType().getType() instanceof GoStructType structure)) return null;
        List<GoFieldDeclaration> payloads = structure.getFieldDeclarationList().stream()
            .filter(f -> f.getFieldDefinitionList().stream().anyMatch(n -> "value".equals(n.getName()) || "Value".equals(n.getName()))).toList();
        long names = payloads.stream().flatMap(f -> f.getFieldDefinitionList().stream()).filter(n -> "value".equals(n.getName()) || "Value".equals(n.getName())).count();
        if (payloads.size() != 1 || names != 1) return null;
        GoType type = payloads.get(0).getType();
        if (type == null || type.getTypeArguments() != null) return null;
        return parameters.stream().filter(p -> Objects.equals(p.getName(), type.getText().trim())).findFirst().orElse(null);
    }
    private static Binding parameterBinding(GoType type, Map<GoTypeParamDefinition, Binding> environment) {
        if (environment.isEmpty()) return null;
        GoTypeSpec owner = PsiTreeUtil.getParentOfType(type, GoTypeSpec.class);
        for (var entry : environment.entrySet()) {
            if (Objects.equals(entry.getKey().getName(), type.getText().trim()) && Objects.equals(PsiTreeUtil.getParentOfType(entry.getKey(), GoTypeSpec.class), owner)) return entry.getValue();
        }
        return null;
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

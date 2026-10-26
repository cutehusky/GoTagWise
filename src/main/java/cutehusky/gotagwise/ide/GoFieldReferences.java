package cutehusky.gotagwise.ide;

import com.goide.psi.*;
import com.goide.psi.impl.GoPsiImplUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import cutehusky.gotagwise.core.*;
import java.util.*;
import static cutehusky.gotagwise.core.ValidatorReferences.Status.*;

/** Resolves Go fields without assuming that a slice index/map entry exists at runtime. */
final class GoFieldReferences implements ValidatorReferences.Lookup {
    private final GoFieldDeclaration field;
    GoFieldReferences(GoFieldDeclaration field) { this.field = field; }
    private record Found(ValidatorReferences.Status status, GoType type) {}
    @Override public ValidatorReferences.Resolved resolve(String path, List<ValidatorReferences.Step> traversal) {
        Found found = resolveType(path, traversal);
        return result(found.status(), found.type());
    }
    private GoType root(List<ValidatorReferences.Step> traversal) {
        GoType current = traversal == null ? PsiTreeUtil.getParentOfType(field, GoStructType.class) : field.getType();
        if (traversal != null) for (var step : traversal) {
            current = normalize(current);
            if (current instanceof GoArrayOrSliceType values && step == ValidatorReferences.Step.VALUE) current = values.getType();
            else if (current instanceof GoMapType map) current = step == ValidatorReferences.Step.KEY ? map.getKeyType() : map.getValueType();
            else return null;
        }
        return current;
    }
    private Found resolveType(String path, List<ValidatorReferences.Step> traversal) {
        GoType current = root(traversal);
        int at = 0;
        while (at < path.length()) {
            ProgressManager.checkCanceled();
            current = normalize(current);
            if (current == null) return new Found(UNKNOWN, null);
            if (GoFieldTypes.of(current, field).unknown()) return new Found(UNKNOWN, null);
            if (path.charAt(at) == '[') {
                int end = path.indexOf(']', at);
                if (end < 0) return new Found(MISSING, null);
                String index = path.substring(at + 1, end);
                if (current instanceof GoArrayOrSliceType values) {
                    try {
                        int number = Integer.parseInt(index);
                        if (number < 0 || values.isArray() && values.getLength() >= 0 && number >= values.getLength()) return new Found(MISSING, null);
                    } catch (NumberFormatException error) { return new Found(MISSING, null); }
                    current = values.getType();
                } else if (current instanceof GoMapType map) {
                    if (ValidatorReferences.literalProblem(GoFieldTypes.of(map.getKeyType(), field), index) != null) return new Found(MISSING, null);
                    current = map.getValueType();
                } else return new Found(MISSING, null);
                at = end + 1;
            } else {
                int start = at;
                while (at < path.length() && path.charAt(at) != '.' && path.charAt(at) != '[') at++;
                String name = path.substring(start, at);
                if (name.isBlank() || !(current instanceof GoStructType structure)) return new Found(MISSING, null);
                Found found = member(structure, name);
                if (found.status() != FOUND) return found;
                current = found.type();
            }
            if (at < path.length() && path.charAt(at) == '.') { at++; if (at == path.length()) return new Found(MISSING, null); }
        }
        return path.isEmpty() ? new Found(MISSING, null) : new Found(FOUND, current);
    }
    record Candidate(String path, FieldType type, boolean navigation) {}
    List<Candidate> complete(ValidatorReferences.Completion context) {
        if (!context.fieldPosition()) return List.of();
        String prefix = context.prefix();
        int dot = -1, brackets = 0;
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c == '[') brackets++;
            if (c == ']') brackets--;
            if (c == '.' && brackets == 0) dot = i;
        }
        if (brackets != 0) return List.of();
        String parent = dot < 0 ? "" : prefix.substring(0, dot);
        GoType container;
        if (parent.isEmpty()) container = root(context.traversal());
        else {
            Found found = resolveType(parent, context.traversal());
            if (found.status() != FOUND) return List.of();
            container = found.type();
        }
        container = normalize(container);
        if (!(container instanceof GoStructType structure)) return List.of();
        Set<String> names = new TreeSet<>();
        collectNames(structure, names, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        List<Candidate> out = new ArrayList<>();
        for (String name : names) {
            String path = (parent.isEmpty() ? "" : parent + ".") + name;
            Found found = resolveType(path, context.traversal());
            if (found.status() != FOUND) continue;
            FieldType type = GoFieldTypes.of(found.type(), field);
            if (ValidatorReferences.supportsCompletion(context, path, type)) out.add(new Candidate(path, type, false));
            if (normalize(found.type()) instanceof GoStructType) out.add(new Candidate(path + ".", type, true));
        }
        return out;
    }
    private void collectNames(GoStructType structure, Set<String> names, Set<GoStructType> seen, int depth) {
        if (depth >= 24 || !seen.add(structure)) return;
        ProgressManager.checkCanceled();
        for (GoFieldDeclaration item : structure.getFieldDeclarationList()) {
            for (var definition : item.getFieldDefinitionList()) if (definition.getName() != null) names.add(definition.getName());
            var embedded = item.getAnonymousFieldDefinition();
            if (embedded != null) {
                if (embedded.getName() != null) names.add(embedded.getName());
                GoType inner = normalize(item.getType() != null ? item.getType() : embedded.getType());
                if (inner instanceof GoStructType child) collectNames(child, names, seen, depth + 1);
            }
        }
    }
    private ValidatorReferences.Resolved result(ValidatorReferences.Status status, GoType type) {
        return new ValidatorReferences.Resolved(status, type == null ? FieldType.UNKNOWN : GoFieldTypes.of(type, field));
    }
    private Found member(GoStructType root, String name) {
        record Path(GoStructType type, List<GoStructType> ancestors) {}
        List<Path> level = List.of(new Path(root, List.of()));
        boolean unknown = false;
        for (int depth = 0; depth < 24 && !level.isEmpty(); depth++) {
            List<Path> next = new ArrayList<>(); List<GoType> matches = new ArrayList<>();
            for (Path path : level) {
                GoStructType structure = path.type();
                if (path.ancestors().contains(structure)) continue;
                for (GoFieldDeclaration item : structure.getFieldDeclarationList()) {
                    var embedded = item.getAnonymousFieldDefinition();
                    if (item.getFieldDefinitionList().stream().anyMatch(f -> name.equals(f.getName())) || embedded != null && name.equals(embedded.getName())) {
                        GoType type = item.getType() != null ? item.getType() : embedded == null ? null : embedded.getType();
                        if (type != null) matches.add(type); else unknown = true;
                    }
                    if (embedded != null) {
                        GoType inner = normalize(item.getType() != null ? item.getType() : embedded.getType());
                        if (inner instanceof GoStructType child) {
                            var ancestors = new ArrayList<>(path.ancestors()); ancestors.add(structure);
                            next.add(new Path(child, ancestors));
                        } else if (inner == null) unknown = true;
                    }
                }
            }
            if (matches.size() > 1) return new Found(AMBIGUOUS, null);
            if (matches.size() == 1) return new Found(FOUND, matches.get(0));
            level = next;
        }
        return new Found(unknown ? UNKNOWN : MISSING, null);
    }
    private GoType normalize(GoType original) {
        GoType type = original;
        Set<GoType> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; type != null && depth < 32 && seen.add(type); depth++) {
            ProgressManager.checkCanceled();
            if (type instanceof GoPointerType pointer) { type = pointer.getType(); continue; }
            if (type instanceof GoSpecType spec) { type = spec.getType(); continue; }
            if (type.getText().startsWith("(")) {
                GoType inner = PsiTreeUtil.getChildOfType(type, GoType.class);
                if (inner != null) { type = inner; continue; }
            }
            var reference = type.getTypeReferenceExpression();
            if (reference == null) return type;
            GoTypeSpec definition = null;
            if (type.getContainingFile() instanceof GoFile file) definition = file.getTypes(type.getText()).stream().findFirst().orElse(null);
            if (definition == null && !DumbService.isDumb(field.getProject())) {
                PsiElement resolved = reference.getReference().resolve();
                if (resolved instanceof GoTypeSpec spec) definition = spec;
            }
            if (definition == null) return null;
            if (GoPsiImplUtil.isBuiltinFile(definition.getContainingFile())) return type;
            type = definition.getSpecType();
        }
        return null;
    }
}

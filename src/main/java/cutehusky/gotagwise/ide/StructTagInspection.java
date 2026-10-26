package cutehusky.gotagwise.ide;

import com.goide.psi.GoTag;
import com.goide.psi.GoVisitor;
import com.goide.psi.GoFieldDeclaration;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import cutehusky.gotagwise.core.TagValidator;
import cutehusky.gotagwise.core.TagTypeValidator;
import cutehusky.gotagwise.core.FieldType;
import cutehusky.gotagwise.core.ValidatorReferences;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class StructTagInspection extends LocalInspectionTool implements DumbAware {
    public boolean reportUnknownRules = true;
    public String customValidatorRules = "";
    public String customSanitizers = "";
    public boolean checkDatatypes = true;
    public boolean assumeHumaParameters = false;
    @Override public @NotNull OptPane getOptionsPane() {
        return OptPane.pane(
            OptPane.checkbox("reportUnknownRules", "Report unknown validation rules, sanitizers and GORM directives"),
            OptPane.checkbox("checkDatatypes", "Check tag compatibility with resolved Go field datatypes"),
            OptPane.checkbox("assumeHumaParameters", "Treat ambiguous form/header tags as Huma request parameters"),
            OptPane.string("customValidatorRules", "Custom validator/binding rules and aliases (comma-separated)"),
            OptPane.string("customSanitizers", "Custom conform sanitizers (comma-separated)"));
    }
    private static Set<String> words(String csv) {
        Set<String> out = new HashSet<>();
        for (String word : csv.split(",")) if (!word.isBlank()) out.add(word.trim());
        return out;
    }
    @Override public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean onTheFly) {
        var options = new TagValidator.Options(reportUnknownRules, words(customValidatorRules), words(customSanitizers));
        return new GoVisitor() {
            @Override public void visitTag(@NotNull GoTag tag) {
                GoFieldDeclaration field = PsiTreeUtil.getParentOfType(tag, GoFieldDeclaration.class);
                FieldType type = field == null ? FieldType.UNKNOWN : GoFieldTypes.of(field);
                var findings = new ArrayList<>(TagValidator.validate(tag.getText(), options, type));
                if (checkDatatypes && field != null) findings.addAll(TagTypeValidator.validate(tag.getText(), type, GoFieldTypes.humaInput(field, assumeHumaParameters), options.customValidators()));
                if (checkDatatypes && field != null) findings.addAll(ValidatorReferences.validate(tag.getText(), type, new GoFieldReferences(field), options.customValidators()));
                for (var finding : findings) {
                    int start = Math.max(0, Math.min(finding.start(), tag.getTextLength() - 1));
                    int end = Math.max(start + 1, Math.min(finding.end(), tag.getTextLength()));
                    LocalQuickFix[] fixes = finding.replacement() != null && tag.getText().startsWith("`")
                        ? new LocalQuickFix[]{new ReplaceValueFix(finding.replacement())} : LocalQuickFix.EMPTY_ARRAY;
                    holder.registerProblem(tag, finding.message(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        new TextRange(start, end), fixes);
                }
            }
        };
    }
    private record ReplaceValueFix(String value) implements LocalQuickFix {
        @Override public @NotNull String getFamilyName() { return "Correct struct tag value"; }
        @Override public @NotNull String getName() { return "Replace with '" + value + "'"; }
        @Override public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null || !element.isValid()) return;
            var document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
            if (document == null) return;
            TextRange range = descriptor.getTextRangeInElement();
            if (range == null) return;
            int base = element.getTextRange().getStartOffset();
            document.replaceString(base + range.getStartOffset(), base + range.getEndOffset(),
                value.replace("\\", "\\\\").replace("\"", "\\\""));
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }
}

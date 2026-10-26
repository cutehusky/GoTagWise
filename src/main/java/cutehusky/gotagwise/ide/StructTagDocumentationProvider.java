package cutehusky.gotagwise.ide;

import com.goide.psi.GoTag;
import com.goide.psi.GoFieldDeclaration;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.platform.backend.documentation.*;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.*;
import cutehusky.gotagwise.core.*;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** Resolve the individual tag at the mouse/caret offset, not the whole Go string. */
public final class StructTagDocumentationProvider implements DocumentationTargetProvider, DumbAware {
    @Override public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        GoTag tag = GoTagContext.tagAt(file, offset);
        if (tag == null) return List.of();
        int relative = offset - tag.getTextRange().getStartOffset();
        for (var entry : TagParser.parse(tag.getText()).entries()) {
            if (relative >= entry.start() && relative < entry.end() && TagCatalog.get(entry.key()) != null) {
                var target = new Target(SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(tag), entry.key(), TagDocumentation.valueIndex(tag.getText(), entry, relative));
                return target.html() == null ? List.of() : List.of(target);
            }
        }
        return List.of();
    }
    private record Target(SmartPsiElementPointer<GoTag> tagPointer, String key, int valueIndex) implements DocumentationTarget {
        @Override public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
            return () -> {
                GoTag tag = tagPointer.getElement();
                if (tag == null || TagParser.parse(tag.getText()).entries().stream().noneMatch(e -> e.key().equals(key))) return null;
                var restored = new Target(tagPointer, key, valueIndex);
                return restored.html() == null ? null : restored;
            };
        }
        @Override public @NotNull TargetPresentation computePresentation() {
            return TargetPresentation.builder(key + ":\"value\"").containerText(TagCatalog.get(key).library()).presentation();
        }
        @Override public String computeDocumentationHint() { return html(); }
        @Override public DocumentationResult computeDocumentation() {
            String html = html();
            return html == null ? null : DocumentationResult.documentation(html);
        }
        private String html() {
            GoTag tag = tagPointer.getElement();
            if (tag == null) return null;
            GoFieldDeclaration field = PsiTreeUtil.getParentOfType(tag, GoFieldDeclaration.class);
            if (field == null) return null;
            var policy = StructTagEditorPolicy.forField(field);
            var entry = TagParser.parse(tag.getText()).entries().stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
            return entry == null ? null : TagDocumentation.html(key, GoTagContext.fieldName(tag), policy.type(), policy.humaInput(), valueIndex, entry.value(), policy.custom(key));
        }
    }
}

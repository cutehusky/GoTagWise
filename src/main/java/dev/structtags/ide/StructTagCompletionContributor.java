package dev.structtags.ide;

import com.goide.psi.GoTag;
import com.goide.psi.GoFieldDeclaration;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import dev.structtags.core.*;

public final class StructTagCompletionContributor extends CompletionContributor implements DumbAware {
    @Override public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        GoTag tag = GoTagContext.tagAt(parameters.getPosition().getContainingFile(), parameters.getOffset());
        if (tag == null) return;
        TagCompletion.Context context = GoTagContext.completion(tag, parameters.getOffset());
        if (context == null) return;
        GoTag originalTag = GoTagContext.tagAt(parameters.getOriginalFile(), parameters.getOffset());
        GoFieldDeclaration field = PsiTreeUtil.getParentOfType(originalTag == null ? tag : originalTag, GoFieldDeclaration.class);
        if (field == null) return;
        StructTagEditorPolicy policy = StructTagEditorPolicy.forField(field);
        var reference = ValidatorReferences.completion(context, policy.type(), policy.custom(context.key()));
        if (reference != null) {
            CompletionResultSet fields = result.withPrefixMatcher(reference.prefix());
            for (var candidate : new GoFieldReferences(field).complete(reference)) {
                fields.addElement(LookupElementBuilder.create(candidate.path())
                    .withTypeText(candidate.navigation() ? "Go field path" : candidate.type().name(), true));
            }
            result.stopHere();
            return;
        }
        CompletionResultSet filtered = result.withPrefixMatcher(context.prefix());
        if (context.keyPosition()) {
            var existing = originalTag == null ? java.util.List.<TagParser.Entry>of() : TagParser.parse(originalTag.getText()).entries();
            for (TagCatalog.Spec spec : TagCatalog.all()) {
                if (existing.stream().anyMatch(e -> e.key().equals(spec.key()))) continue;
                if (!TagTypeSupport.supportsKey(spec.key(), policy.type(), policy.humaInput())) continue;
                filtered.addElement(LookupElementBuilder.create(spec.key()).withTypeText(spec.library(), true)
                    .withInsertHandler((insertion, item) -> {
                        int end = insertion.getTailOffset();
                        CharSequence text = insertion.getDocument().getCharsSequence();
                        if (end >= text.length() || text.charAt(end) != ':') {
                            insertion.getDocument().insertString(end, ":\"\"");
                            insertion.getEditor().getCaretModel().moveToOffset(end + 2);
                            insertion.setTailOffset(end + 3);
                            insertion.setAddCompletionChar(false);
                        }
                    }));
            }
        } else {
            for (String value : TagCompletion.values(context, GoTagContext.fieldName(tag), policy.type(), policy.humaInput(), policy.custom(context.key()))) {
                var spec = TagCatalog.get(context.key());
                filtered.addElement(LookupElementBuilder.create(value).withTypeText(spec == null ? "Struct tag" : spec.library(), true));
            }
        }
        result.runRemainingContributors(parameters, completion -> {
            if (TagTypeSupport.supportsLookup(context, completion.getLookupElement().getLookupString(), policy.type(), policy.humaInput(), policy.custom(context.key()))) result.passResult(completion);
        });
        result.stopHere();
    }
    @Override public boolean invokeAutoPopup(com.intellij.psi.PsiElement position, char typed) {
        return (typed == ',' || typed == ';' || typed == '"' || typed == '|' || typed == ' ' || typed == '=' || typed == '.')
                && com.intellij.psi.util.PsiTreeUtil.getParentOfType(position, GoTag.class, false) != null;
    }
}

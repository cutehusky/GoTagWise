package dev.structtags.ide;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import org.jetbrains.annotations.NotNull;
import com.goide.psi.GoFieldDeclaration;
import com.intellij.psi.util.PsiTreeUtil;
import dev.structtags.core.*;

public final class StructTagTemplateContext extends TemplateContextType {
    public StructTagTemplateContext() { super("Go struct tag key (GoTagWise)"); }
    @Override public boolean isInContext(@NotNull TemplateActionContext context) {
        var tag = GoTagContext.tagAt(context.getFile(), context.getStartOffset());
        if (tag == null) return false;
        var state = GoTagContext.completion(tag, context.getStartOffset());
        if (state == null || !state.keyPosition()) return false;
        String prefix = state.prefix();
        String key = prefix.equals("sthuma") ? "minLength" : prefix.startsWith("st") ? prefix.substring(2) : "";
        if (TagCatalog.get(key) == null) return true;
        GoFieldDeclaration field = PsiTreeUtil.getParentOfType(tag, GoFieldDeclaration.class);
        if (field == null) return false;
        var policy = StructTagEditorPolicy.forField(field);
        return TagTypeSupport.supportsKey(key, policy.type(), policy.humaInput());
    }
}

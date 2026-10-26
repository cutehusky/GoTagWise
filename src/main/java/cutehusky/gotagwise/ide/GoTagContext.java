package cutehusky.gotagwise.ide;

import com.goide.psi.GoFieldDeclaration;
import com.goide.psi.GoTag;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import cutehusky.gotagwise.core.TagCompletion;

final class GoTagContext {
    private GoTagContext() {}
    static GoTag tagAt(PsiFile file, int offset) {
        if (!file.getLanguage().getID().equals("go") && !file.getLanguage().getID().equals("Go")) return null;
        PsiElement element = file.findElementAt(Math.max(0, Math.min(offset, file.getTextLength() - 1)));
        GoTag tag = PsiTreeUtil.getParentOfType(element, GoTag.class, false);
        if (tag == null && offset > 0) tag = PsiTreeUtil.getParentOfType(file.findElementAt(offset - 1), GoTag.class, false);
        return tag;
    }
    static String fieldName(GoTag tag) {
        GoFieldDeclaration field = PsiTreeUtil.getParentOfType(tag, GoFieldDeclaration.class);
        if (field == null || field.getFieldDefinitionList().isEmpty()) return "field";
        String name = field.getFieldDefinitionList().get(0).getName();
        return name == null ? "field" : name;
    }
    static TagCompletion.Context completion(GoTag tag, int offset) {
        String text = tag.getText();
        int relative = offset - tag.getTextRange().getStartOffset();
        // Literal escape mapping is handled by inspection; editor insertion targets raw tags.
        if (!text.startsWith("`") || relative < 1 || relative > text.length() ||
                relative == text.length() && text.endsWith("`")) return null;
        return TagCompletion.context(text.substring(1, relative));
    }
}

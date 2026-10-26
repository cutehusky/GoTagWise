package cutehusky.gotagwise.ide;

import com.intellij.codeInsight.template.*;
import cutehusky.gotagwise.core.TagCatalog;
import org.jetbrains.annotations.NotNull;

public final class StructTagNameMacro extends Macro {
    @Override public @NotNull String getName() { return "smartGoTagName"; }
    @Override public @NotNull String getPresentableName() { return "smartGoTagName(style)"; }
    @Override public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
        var file = context.getPsiFile();
        if (file == null) return new TextResult("field");
        var tag = GoTagContext.tagAt(file, context.getStartOffset());
        String style = params.length == 0 ? "snake" : String.valueOf(params[0].calculateResult(context));
        return new TextResult(TagCatalog.name(tag == null ? "field" : GoTagContext.fieldName(tag), style));
    }
}

package dev.structtags.ide;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import org.jetbrains.annotations.NotNull;

public final class StructTagTemplates implements DefaultLiveTemplatesProvider {
    @Override public @NotNull String[] getDefaultLiveTemplateFiles() { return new String[]{"/liveTemplates/GoTagWise"}; }
    @Override public @NotNull String[] getHiddenLiveTemplateFiles() { return new String[0]; }
}

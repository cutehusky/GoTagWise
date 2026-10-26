package dev.structtags.core;

import java.nio.file.*;

/** Run explicitly to regenerate bundled templates after changing the catalog. */
public final class TemplateGenerator {
    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    public static String generate() {
        StringBuilder out = new StringBuilder("<templateSet group=\"GoTagWise\">\n");
        for (var spec : TagCatalog.all()) {
            String expression = spec.kind() == TagCatalog.Kind.NAME_OPTIONS
                    ? "smartGoTagName(\"" + (spec.key().equals("env") ? "upper" : spec.key().equals("json") ? "camel" : "snake") + "\")" : "";
            out.append("  <template name=\"st").append(spec.key()).append("\" value=\"").append(xml(spec.key() + ":\"$VALUE$\"$END$"))
                .append("\" description=\"").append(xml(spec.library() + ": " + spec.key()))
                .append("\" toReformat=\"false\" toShortenFQNames=\"false\">\n")
                .append("    <variable name=\"VALUE\" expression=\"").append(xml(expression)).append("\" defaultValue=\"")
                .append(xml("\"" + spec.example() + "\"")).append("\" alwaysStopAt=\"true\"/>\n")
                .append("    <context><option name=\"GoTagWise\" value=\"true\"/></context>\n  </template>\n");
        }
        out.append("  <template name=\"sthuma\" value=\"json:&quot;$NAME$&quot; doc:&quot;$DOC$&quot; minLength:&quot;$MIN$&quot;$END$\" description=\"Huma v2 string schema\" toReformat=\"false\" toShortenFQNames=\"false\">\n")
            .append("    <variable name=\"NAME\" expression=\"smartGoTagName(&quot;camel&quot;)\" defaultValue=\"&quot;field&quot;\" alwaysStopAt=\"true\"/>\n")
            .append("    <variable name=\"DOC\" expression=\"\" defaultValue=\"&quot;Description&quot;\" alwaysStopAt=\"true\"/>\n")
            .append("    <variable name=\"MIN\" expression=\"\" defaultValue=\"&quot;1&quot;\" alwaysStopAt=\"true\"/>\n")
            .append("    <context><option name=\"GoTagWise\" value=\"true\"/></context>\n  </template>\n");
        return out.append("</templateSet>\n").toString();
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of("src/main/resources/liveTemplates/GoTagWise.xml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, generate());
    }
}

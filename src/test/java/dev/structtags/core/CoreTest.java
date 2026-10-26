package dev.structtags.core;

import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Dependency-free regression suite: run by Gradle check and build-local.ps1. */
public final class CoreTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void valid(String tag) { check(TagValidator.validate(tag, TagValidator.Options.defaults()).isEmpty(), "Should accept " + tag + ": " + TagValidator.validate(tag, TagValidator.Options.defaults())); }
    private static void invalid(String tag, String message) { check(TagValidator.validate(tag, TagValidator.Options.defaults()).stream().anyMatch(f -> f.message().contains(message)), "Should report " + message + " for " + tag); }
    public static void main(String[] args) throws Exception {
        checks += TypeValidationTest.run();
        checks += CompletionTypeTest.run();
        checks += ValidatorReferenceTest.run();
        valid("`json:\"id,omitempty\" bson:\"_id,minsize\" yaml:\",inline\"`");
        valid("`json:\",omitzero\" jsonv2:\"anything\"`");
        valid("`dynamodbav:\",stringset,omitemptyelem\"`");
        valid("`env:\"PORT,required,notEmpty,expand\" envDefault:\"3000\"`");
        valid("`form:\"name,default=Guest\" binding:\"required,email\"`");
        valid("`validate:\"omitempty,oneof='New York' 'Los Angeles',min=1|eq=0\"`");
        valid("`validate:\"dive,keys,required,endkeys,unique\"`");
        valid("`validate:\"\" conform:\"\"`");
        valid("`msgpack:\",inline\" literal:\"true\"`");
        valid("`hcl:\"server,block\"`");
        valid("`hcl:\",remain\"`");
        valid("`xml:\"urn:example name,attr,omitempty\"`");
        valid("`toml:\"field,omitempty\" commented:\"true\"`");
        valid("`redis:\"name,omitempty\"`");
        valid("`msgpack:\"field,omitempty\"`");
        valid("`gorm:\"column:email;size:255;not null;index:ix_email,unique;autoUpdateTime:milli\"`");
        valid("`gorm:\"PRIMARYKEY;->;<-:create;foreignKey:UserID\"`");
        valid("`bun:\"price,type:decimal(10,2),default:coalesce(price,0),notnull\"`");
        valid("`bun:\"table:users,alias:u\"`");
        valid("`bun:\",rel:has-many,join:id=user_id\"`");
        valid("`conform:\"trim,lower,!html\"`");
        valid("`json:\"name\" minLength:\"0\" maxLength:\"80\" readOnly:\"true\" minimum:\"-2.5\" multipleOf:\"0.5\"`");
        valid("`custom:\"some,value\"`");
        valid("\"json:\\\"id,omitempty\\\"\"");
        valid("`doc:\"say \\\"hello\\\"\\nnext\"`");
        invalid("`json:\"id,omitemtpy\"`", "Unknown json option");
        var typo = TagValidator.validate("`json:\"id,omitemtpy\"`", TagValidator.Options.defaults()).get(0);
        check("id,omitempty".equals(typo.replacement()), "Typo fix retains field name");
        invalid("`bson:\"id,bad\"`", "Unknown bson");
        invalid("`dynamodbav:\",omitempty,nullempty\"`", "Conflicting");
        invalid("`dynamodbav:\",numberset,stringset\"`", "only one");
        invalid("`env:\"PORT,require\"`", "Unknown env");
        invalid("`xml:\",attr,chardata\"`", "cannot be combined");
        invalid("`validate:\"min\"`", "requires a parameter");
        invalid("`binding:\"email=yes\"`", "does not accept");
        invalid("`binding:\"required,,email\"`", "Empty validation");
        invalid("`validate:\"my_custom\"`", "Unknown validate rule");
        var custom = new TagValidator.Options(true, Set.of("my_custom"), Set.of("slugify"));
        check(TagValidator.validate("`validate:\"my_custom\" conform:\"slugify\"`", custom).isEmpty(), "Custom vocabulary");
        var permissive = new TagValidator.Options(false, Set.of(), Set.of());
        check(TagValidator.validate("`validate:\"future_rule\" gorm:\"plugin:yes\"`", permissive).isEmpty(), "Unknown-rule switch");
        invalid("`gorm:\"size:large\"`", "non-negative integer");
        invalid("`gorm:\"autoUpdateTime:seconds\"`", "Expected nano");
        invalid("`minLength:\"-1\"`", "non-negative integer");
        invalid("`maxItems:\"1.2\"`", "non-negative integer");
        invalid("`minimum:\"NaN\"`", "finite numeric");
        invalid("`readOnly:\"yes\"`", "boolean");
        invalid("`multipleOf:\"0\"`", "greater than zero");
        invalid("`minimum:\"10\" maximum:\"5\"`", "must be at least");
        invalid("`json:\"a\" json:\"b\"`", "Duplicate");
        invalid("`json:a`", "Expected key");
        invalid("`json:\"a\"yaml:\"b\"`", "space");
        invalid("`json:\"bad\\q\"`", "Invalid Go escape");
        invalid("`json:\"bad`", "Unclosed");
        var parsed = TagParser.parse("`json:\"id\" bson:\"_id\"`");
        check(parsed.entries().get(1).valueStart() == 17, "Raw literal range");
        var interpreted = TagParser.parse("\"json:\\\"id\\\"\"").entries().get(0);
        check(interpreted.valueStart() == 8 && interpreted.valueEnd() == 10, "Interpreted literal ranges");
        check(GoStrings.decode("\\u263a\\U0001f600", 0, false).text().equals("☺😀"), "Go Unicode escapes");
        check(GoStrings.decode("\\xc3\\xa9", 5, false).text().equals("é"), "Go UTF-8 byte escapes");
        check(GoStrings.decode("\\360\\237\\230\\200", 0, false).text().equals("😀"), "Go UTF-8 octal bytes");
        check(GoStrings.decode("\\xc3\\xa9x", 5, false).offset(1) == 13, "UTF-8 escape source offsets");
        for (String malformed : List.of("\\uD800", "\\U00110000", "\\777", "\\xGG", "\\u12")) {
            boolean rejected = false;
            try { GoStrings.decode(malformed, 0, false); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Reject " + malformed);
        }
        check(TagCatalog.name("HTTPServerID", "snake").equals("http_server_id"), "Acronym snake case");
        check(TagCatalog.name("HTTPServer", "camel").equals("httpServer"), "Acronym camel case");
        check(TagCatalog.name("DBPort", "upper").equals("DB_PORT"), "Environment name");
        check(TagCompletion.context("json:\"name\" ya").prefix().equals("ya"), "Key prefix");
        check(TagCompletion.context("doc:\"json: \\\"example\\\"\" stjson").keyPosition(), "Escaped quotes in preceding tag");
        var ctx = TagCompletion.context("json:\"id,omi");
        check(!ctx.keyPosition() && ctx.prefix().equals("omi"), "Option prefix");
        check(TagCompletion.values(ctx, "ID").contains("omitempty"), "JSON options");
        check(TagCompletion.values(TagCompletion.context("env:\""), "DBPort").contains("DB_PORT"), "Field-aware values");
        check(!TagCompletion.values(TagCompletion.context("validate:\"required,"), "Email").contains("required"), "No repeated rule suggestions");
        check(TagCompletion.context("json:\"foo\"").keyPosition(), "After value");
        check(TagCompletion.context("json: ") == null, "Malformed completion context");
        check(TagCompletion.context("validate:\"required|em").prefix().equals("em"), "OR completion");
        check(TagValidator.split("type:decimal(10,2),default:'a,b',notnull", ',').size() == 3, "SQL comma splitting");
        String queryDocs = TagDocumentation.html("query", "Search");
        check(queryDocs.contains("Huma v2") && queryDocs.contains("?q=hello"), "Query documentation identifies Huma and URL binding");
        check(queryDocs.contains("explode") && queryDocs.contains("?tags=go&amp;tags=api"), "Query docs show repeated parameters");
        check(queryDocs.contains("required:&quot;true&quot;") && queryDocs.contains("request-inputs/"), "Query docs show optionality and source link");
        check(TagDocumentation.html("unknown", "Field") == null, "No invented docs for custom tag keys");
        check(!TagDocumentation.html("json", "<script>").contains("<script>"), "Documentation escapes field names");
        FieldType integers = FieldType.container(FieldType.Kind.SLICE, FieldType.of(FieldType.Kind.INTEGER, "int"), null);
        String jsonDocs = TagDocumentation.html("json", "Values", integers, false, -1, "values", Set.of());
        check(jsonDocs.contains("<code>omitempty</code>") && !jsonDocs.contains("<code>string</code>"), "JSON documentation lists only supported options");
        check(TagDocumentation.html("minLength", "Count", FieldType.of(FieldType.Kind.INTEGER, "int"), false, -1, "1", Set.of()) == null, "Unsupported tag key has no documentation");
        check(TagDocumentation.html("json", "Values", integers, false, 8, "values,string", Set.of()) == null, "Unsupported option has no documentation");
        check(TagDocumentation.html("validate", "Count", FieldType.of(FieldType.Kind.INTEGER, "int"), false, 0, "email", Set.of()) == null, "Unsupported validation rule has no documentation");
        String rulesDocs = TagDocumentation.html("validate", "Count", FieldType.of(FieldType.Kind.INTEGER, "int"), false, -1, "", Set.of());
        check(rulesDocs.contains("<code>numeric</code>") && !rulesDocs.contains("<code>email</code>"), "Validator documentation filters rules by datatype");
        FieldType strings = FieldType.container(FieldType.Kind.SLICE, FieldType.of(FieldType.Kind.STRING, "string"), null);
        check(TagDocumentation.html("validate", "Emails", strings, false, 6, "dive,email", Set.of()).contains("<code>email</code>"), "Documentation follows dive element type");
        check(TagDocumentation.html("validate", "Count", FieldType.of(FieldType.Kind.INTEGER, "int"), false, 0, "email", Set.of("email")) != null, "Documentation respects custom rule overrides");
        String escapedLiteral = "\"json:\\\"values,string\\\"\"";
        var escapedEntry = TagParser.parse(escapedLiteral).entries().get(0);
        check(TagDocumentation.valueIndex(escapedLiteral, escapedEntry, escapedLiteral.indexOf("string")) == 7, "Interpreted tag documentation offset mapping");
        // Every catalog key has documentation and an example accepted by the inspector.
        for (var spec : TagCatalog.all()) {
            String html = TagDocumentation.html(spec.key(), "Field");
            check(html != null && html.contains("Upstream documentation") && !TagDocumentation.description(spec.key()).isBlank(), "Documentation for " + spec.key());
        }
        // Every catalog example is accepted by the inspector.
        for (var spec : TagCatalog.all()) valid("`" + spec.key() + ":\"" + spec.example() + "\"`");
        Path resources = Path.of("src/main/resources");
        String templates = Files.readString(resources.resolve("liveTemplates/GoTagWise.xml"));
        check(templates.equals(TemplateGenerator.generate()), "Templates match shared catalog");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = factory.newDocumentBuilder().parse(resources.resolve("liveTemplates/GoTagWise.xml").toFile());
        check(doc.getElementsByTagName("template").getLength() == TagCatalog.all().size() + 1, "Template coverage");
        factory.newDocumentBuilder().parse(resources.resolve("META-INF/plugin.xml").toFile());
        System.out.println("Passed " + checks + " core and resource checks; " + TagCatalog.all().size() + " tag keys.");
    }
}

package dev.structtags.smoke;

import com.goide.GoLanguage;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.codeInspection.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.goide.psi.GoTag;
import com.intellij.openapi.util.Key;
import com.intellij.patterns.ElementPattern;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.*;
import dev.structtags.ide.*;
import java.nio.file.*;
import java.util.*;

/** Test-only project activity; never included in the distributed plugin. */
public final class EditorSmokeStarter implements StartupActivity.DumbAware {
    private int checks;
    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    @Override public void runActivity(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            String report;
            try {
                ReadAction.run(() -> run(project));
                WriteCommandAction.runWriteCommandAction(project, () -> runFix(project));
                report = "Passed " + checks + " IDE editor checks.\n";
            } catch (Throwable failure) {
                failure.printStackTrace();
                var trace = new java.io.StringWriter();
                failure.printStackTrace(new java.io.PrintWriter(trace));
                report = "FAILED: " + trace;
            }
            try { Files.writeString(Path.of(System.getProperty("structtags.smoke.report")), report); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }
    private PsiFile file(Project project, String source) {
        return PsiFileFactory.getInstance(project).createFileFromText("smoke.go", GoLanguage.INSTANCE, "package smoke\ntype T struct {\n" + source + "\n}\n");
    }
    private void run(Project project) {
        var templates = TemplateSettings.getInstance();
        check(templates.getTemplate("stjson", "GoTagWise") != null, "JSON template loaded");
        check(templates.getTemplate("sthuma", "GoTagWise") != null, "Huma template loaded");
        check(templates.getTemplatesAsList().stream().filter(t -> t.getGroupName().equals("GoTagWise")).count() == 63, "All templates loaded");
        var context = new StructTagTemplateContext();
        PsiFile key = file(project, "HTTPServerID string `stjson`");
        int offset = key.getText().indexOf("stjson") + 6;
        check(context.isInContext(TemplateActionContext.expanding(key, offset)), "Template enabled in raw tag key");
        PsiFile value = file(project, "Name string `json:\"stjson\"`");
        check(!context.isInContext(TemplateActionContext.expanding(value, value.getText().indexOf("stjson") + 6)), "No template inside tag value");
        PsiFile ordinary = file(project, "Name string // stjson");
        check(!context.isInContext(TemplateActionContext.expanding(ordinary, ordinary.getText().indexOf("stjson") + 6)), "No template in comments");
        PsiFile interpreted = file(project, "Name string \"json:\\\"name\\\"\"");
        check(!context.isInContext(TemplateActionContext.expanding(interpreted, interpreted.getText().indexOf("json"))), "No raw-tag template in interpreted strings");

        var factory = EditorFactory.getInstance();
        Document document = PsiDocumentManager.getInstance(project).getDocument(key);
        if (document == null) throw new AssertionError("No Go document");
        Editor editor = factory.createEditor(document, project);
        try {
            int at = offset;
            ExpressionContext expressionContext = new ExpressionContext() {
                public Project getProject() { return project; }
                public Editor getEditor() { return editor; }
                public PsiFile getPsiFile() { return key; }
                public int getStartOffset() { return at; }
                public int getTemplateStartOffset() { return at; }
                public int getTemplateEndOffset() { return at; }
                public <T> T getProperty(Key<T> property) { return null; }
                public PsiElement getPsiElementAtStartOffset() { return key.findElementAt(at); }
                public TextResult getVariableValue(String name) { return null; }
            };
            check(new StructTagNameMacro().calculateResult(new Expression[0], expressionContext).toString().equals("http_server_id"), "Field name macro uses Go PSI");
        } finally { factory.releaseEditor(editor); }
        check(complete(project, "Name string `js<caret>`").contains("json"), "Key contributor suggests json");
        check(!complete(project, "Name string `json:\"name\" js<caret>`").contains("json"), "Existing tag key not repeated");
        check(complete(project, "Name string `json:\"name,omi<caret>\"`").contains("omitempty"), "JSON value completion");
        check(complete(project, "DBPort int `env:\"<caret>\"`").contains("DB_PORT"), "Environment field name completion");
        check(complete(project, "Name string `validate:\"required|em<caret>\"`").contains("email"), "OR rule completion");
        check(complete(project, "Name string // js<caret>").isEmpty(), "Completion ignores comments");
        var docs = new StructTagDocumentationProvider();
        check(DocumentationTargetProvider.EP_NAME.getExtensionList().stream().anyMatch(p -> p instanceof StructTagDocumentationProvider), "Documentation target provider registered");
        PsiFile documented = file(project, "Search string `json:\"search\" query:\"q\" doc:\"Search text\"`");
        int queryOffset = documented.getText().indexOf("query");
        var targets = docs.documentationTargets(documented, queryOffset + 2);
        check(targets.size() == 1, "Hover resolves query key inside a multi-tag Go literal");
        var target = targets.get(0);
        check(target.computeDocumentationHint().contains("Huma v2") && target.computeDocumentationHint().contains("?q=hello"), "Hover shows Huma query documentation");
        check(target.computeDocumentation() != null && target.computePresentation().getContainerText().equals("Huma v2"), "Quick Documentation result and library presentation");
        check(target.createPointer().dereference() != null, "Documentation pointer restores live tag target");
        check(docs.documentationTargets(documented, queryOffset + 7).get(0).computeDocumentationHint().contains("Huma v2"), "Hover over query value resolves its tag");
        check(docs.documentationTargets(documented, documented.getText().indexOf("json")).get(0).computeDocumentationHint().contains("encoding/json"), "Neighboring tag has its own documentation");
        check(docs.documentationTargets(documented, queryOffset - 1).isEmpty(), "No documentation on tag separator whitespace");
        PsiFile custom = file(project, "Name string `custom:\"query\"`");
        check(docs.documentationTargets(custom, custom.getText().indexOf("custom")).isEmpty(), "Custom keys keep existing documentation behavior");
        check(docs.documentationTargets(ordinary, ordinary.getText().indexOf("stjson")).isEmpty(), "No struct tag docs in comments");
        check(docs.documentationTargets(interpreted, interpreted.getText().indexOf("json")).size() == 1, "Documentation supports interpreted Go tags");
        runDatatypeChecks(project);
        runTypedCompletionChecks(project);
        runTypedDocumentationChecks(project);
        runReferenceChecks(project);
        runReferenceCompletionChecks(project);
        runGenericWrapperChecks(project);
    }
    private void runReferenceCompletionChecks(Project project) {
        String fields = "Description *string `json:\"description,omitempty\"`; Count int; Wide int64; Enabled bool; ";
        check(complete(project, fields + "Title string `validate:\"excluded_if=Des<caret>\"`").contains("Description"), "Conditional references complete Go field names");
        check(!complete(project, fields + "Title string `validate:\"excluded_if=<caret>\"`").contains("description"), "Reference completion excludes JSON aliases");
        check(complete(project, fields + "Title string `binding:\"excluded_if=Des<caret>\"`").contains("Description"), "Gin binding reference completion");
        check(complete(project, fields + "Title string `validate:\"required|excluded_if=Des<caret>\"`").contains("Description"), "References inside OR rules");
        check(complete(project, fields + "Title string `validate:\"excluded_if=Description <caret>\"`").isEmpty(), "No field suggestions in literal slot");
        check(complete(project, fields + "Title string `validate:\"excluded_if=Description 'New <caret>\"`").isEmpty(), "No field suggestions inside quoted literal");
        check(complete(project, fields + "Title string `validate:\"excluded_if=Description '' Co<caret>\"`").contains("Count"), "Next conditional reference after empty string");
        check(!complete(project, fields + "Title string `validate:\"required_if=Description '' Des<caret>\"`").contains("Description"), "No duplicate required_if reference");
        check(complete(project, fields + "Title string `validate:\"excluded_if=Description '' Des<caret>\"`").contains("Description"), "Other conditional rules may repeat references");
        check(complete(project, fields + "Title string `validate:\"required_with=Enabled Co<caret>\"`").contains("Count"), "Presence reference accepts different field types");
        Set<String> comparison = complete(project, fields + "Title string `validate:\"eqfield=<caret>\"`");
        check(comparison.contains("Description") && !comparison.contains("Count") && !comparison.contains("Enabled"), "Comparison references filter incompatible types");
        Set<String> numeric = complete(project, fields + "Number int `validate:\"gtefield=<caret>\"`");
        check(numeric.contains("Count") && !numeric.contains("Wide"), "Numeric references match exact Go numeric kind");
        check(complete(project, fields + "Title string `validate:\"fieldcontains=Des<caret>\"`").contains("Description"), "String reference rule completion");
        check(complete(project, fields + "Titles []int `validate:\"dive,eqfield=Co<caret>\"`").contains("Count"), "Reference completion follows dive element datatype");
        String address = "package smoke\ntype Address struct { City string; Zip int }; type T struct { Addr *Address; Items []Address; Lookup map[string]Address; Title string `validate:\"eqcsfield=";
        check(completeSource(project, address + "Addr.C<caret>\"` }").contains("Addr.City"), "Nested pointer field path completion");
        check(completeSource(project, address + "Items[0].C<caret>\"` }").contains("Items[0].City"), "Slice indexed field path completion");
        check(completeSource(project, address + "Lookup[a.b].C<caret>\"` }").contains("Lookup[a.b].City"), "Map keys containing dots retain path prefix");
        check(completeSource(project, address + "<caret>\"` }").contains("Addr."), "Struct navigation offered for nested comparison");
        check(completeSource(project, "package smoke\ntype Item struct { ID string; Data []string }; type T struct { Items []Item `validate:\"unique=<caret>\"` }").equals(Set.of("ID")), "Unique completes comparable element fields only");
        check(completeSource(project, "package smoke\ntype Item struct { ID string }; type T struct { Items [][]Item `validate:\"dive,unique=I<caret>\"` }").contains("ID"), "Unique completion follows nested collection traversal");
        check(completeSource(project, "package smoke\ntype Base struct { Code string }; type T struct { Base; Name string `validate:\"eqfield=Co<caret>\"` }").contains("Code"), "Promoted fields offered");
        check(completeSource(project, "package smoke\ntype A struct { Code string }; type B struct { Code string }; type T struct { A; B; Name string `validate:\"eqfield=Co<caret>\"` }").isEmpty(), "Ambiguous promoted fields excluded");
        check(completeSource(project, "package smoke\ntype Wrap[T any] struct { Value T }; type T struct { Description *string; Title Wrap[string] `validate:\"excluded_if=Des<caret>\"` }").contains("Description"), "Generic wrappers preserve parent reference scope");
        check(complete(project, "Count int; Name string `validate:\"excluded_if=Count ''<caret>\"`").isEmpty(), "Quoted values never become field names");
    }
    private void runGenericWrapperChecks(Project project) {
        String wrap = "package smoke\ntype Wrap[T any] struct { value T }\n";
        typeCheck(project, wrap + "type T struct { Email Wrap[string] `validate:\"email\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Count Wrap[int] `validate:\"email\"` }", false, "string field");
        typeCheck(project, wrap + "type T struct { Count Wrap[int] `minimum:\"1\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Text Wrap[string] `minimum:\"1\"` }", false, "numeric field");
        typeCheck(project, wrap + "type T struct { Text Wrap[Wrap[string]] `validate:\"email\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Emails []Wrap[string] `validate:\"dive,email\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Emails Wrap[[]string] `validate:\"dive,email\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Emails map[string]Wrap[string] `validate:\"dive,email\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Emails Wrap[[]int] `json:\",string\"` }", false, "only supported");
        typeCheck(project, wrap + "type T struct { Query *Wrap[string] `query:\"q\"` }", false, "does not support pointer");
        typeCheck(project, wrap + "type T struct { Query Wrap[*string] `query:\"q\"` }", false, null);
        typeCheck(project, wrap + "type Alias = Wrap[string]\ntype T struct { Email Alias `validate:\"email\"` }", false, null);
        typeCheck(project, wrap + "type Alias[T any] = Wrap[T]\ntype T struct { Email Alias[string] `validate:\"email\"` }", false, null);
        typeCheck(project, wrap + "type Alias[T any] = Wrap[[]T]\ntype T struct { Emails Alias[string] `validate:\"dive,email\"` }", false, null);
        typeCheck(project, wrap + "type Alias[K comparable,V any] = Wrap[map[K]V]\ntype T struct { Emails Alias[int,string] `validate:\"dive,keys,numeric,endkeys,email\"` }", false, null);
        typeCheck(project, wrap + "func (Wrap[T]) Validate() error { return nil }\ntype T struct { Email Wrap[string] `validate:\"validateFn\"` }", false, null);
        typeCheck(project, wrap + "type Defined Wrap[string]\ntype T struct { Data Defined `minProperties:\"1\"` }", false, null);
        typeCheck(project, wrap + "type Defined Wrap[string]\ntype T struct { Email Defined `validate:\"email\"` }", false, "string field");
        typeCheck(project, "package smoke\ntype Wrap[A,B any] struct { Value B; Set bool }\ntype T struct { Email Wrap[int,string] `validate:\"email\"` }", false, null);
        typeCheck(project, "package smoke\ntype Wrap[A,B any] struct { Value B }\ntype T struct { Count Wrap[string,int] `minimum:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype Wrap[T any] struct { value, Other T }\ntype T struct { Email Wrap[string] `validate:\"email\"` }", false, null);
        typeCheck(project, "package smoke\ntype Plain struct { value string }\ntype T struct { Email Plain `validate:\"email\"` }", false, "string field");
        typeCheck(project, "package smoke\ntype Fixed[T any] struct { value string }\ntype T struct { Email Fixed[string] `validate:\"email\"` }", false, "string field");
        typeCheck(project, "package smoke\ntype Box[T any] struct { Data T }\ntype T struct { Data Box[string] `minProperties:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype Box[T any] struct { value []T }\ntype T struct { Data Box[string] `minProperties:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype Box[T any] struct { value *T }\ntype T struct { Data Box[string] `minProperties:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype Box[T any] struct { value T; Value T }\ntype T struct { Data Box[string] `minProperties:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype Box[T any] struct { value Box[T] }\ntype T struct { Data Box[string] `minProperties:\"1\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Count int; Other Wrap[int] `validate:\"eqfield=Count\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Flag Wrap[bool]; Name string `validate:\"required_if=Flag yes\"` }", false, "true or false");
        typeCheck(project, wrap + "type T struct { Flag Wrap[bool]; Name string `validate:\"required_if=Flag true\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Email Wrap[unresolved.Type] `validate:\"email\"` }", false, null);
        typeCheck(project, wrap + "type T struct { Email Wrap `validate:\"email\"` }", false, null);
        check(completeSource(project, wrap + "type T struct { Count Wrap[int] `validate:\"em<caret>\"` }").isEmpty(), "Wrapper integer hides email completion");
        check(completeSource(project, wrap + "type T struct { Email Wrap[string] `validate:\"em<caret>\"` }").contains("email"), "Wrapper string offers email completion");
        check(completeSource(project, wrap + "type T struct { Query Wrap[string] `qu<caret>` }").contains("query"), "Value wrapper offers Huma query completion");
        check(completeSource(project, wrap + "type T struct { Query *Wrap[string] `qu<caret>` }").isEmpty(), "Physical pointer wrapper hides Huma query completion");
        PsiFile documented = PsiFileFactory.getInstance(project).createFileFromText("wrappers.go", GoLanguage.INSTANCE, wrap + "type T struct { Email Wrap[string] `validate:\"email\"` }");
        var targets = new StructTagDocumentationProvider().documentationTargets(documented, documented.getText().lastIndexOf("email") + 1);
        check(targets.size() == 1 && targets.get(0).computeDocumentationHint().contains("valid email address"), "Wrapper hover uses inferred argument datatype");
    }
    private Set<String> completeSource(Project project, String marked) {
        int offset = marked.indexOf("<caret>");
        PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("wrappers.go", GoLanguage.INSTANCE, marked.replace("<caret>", ""));
        EditorFactory factory = EditorFactory.getInstance();
        Editor editor = factory.createEditor(Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(file)), project);
        try {
            var contributor = new StructTagCompletionContributor();
            Set<String> values = new HashSet<>();
            var params = new CompletionParameters(Objects.requireNonNull(file.findElementAt(offset)), file, CompletionType.BASIC, offset, 1, editor, () -> false);
            contributor.fillCompletionVariants(params, new CapturingResult(PrefixMatcher.ALWAYS_TRUE, contributor, values, List.of()));
            return values;
        } finally { factory.releaseEditor(editor); }
    }
    private void runReferenceChecks(Project project) {
        typeCheck(project, "package smoke\ntype T struct { Count int; Confirm int `validate:\"eqfield=Count\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Confirm int `validate:\"eqfield=Missing\"` }", false, "missing or invalid");
        typeCheck(project, "package smoke\ntype T struct { Count string; Confirm int `validate:\"eqfield=Count\"` }", false, "incompatible datatypes");
        typeCheck(project, "package smoke\ntype T struct { Count int64; Confirm int `validate:\"gtefield=Count\"` }", false, "same Go numeric kind");
        typeCheck(project, "package smoke\ntype T struct { Count *int; Confirm int `validate:\"eqfield=Count\"` }", false, null);
        typeCheck(project, "package smoke\ntype Address struct { Code string }; type T struct { Address *Address; Confirm string `validate:\"eqcsfield=Address.Code\"` }", false, null);
        typeCheck(project, "package smoke\ntype Address struct { Code string }; type T struct { Address *Address; Confirm string `validate:\"eqcsfield=Address.Missing\"` }", false, "missing or invalid");
        typeCheck(project, "package smoke\ntype T struct { Count int; Email string `validate:\"required_with=Count\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Age int; Email string `validate:\"required_if=Age wrong\"` }", false, "integer literal");
        typeCheck(project, "package smoke\ntype T struct { Enabled bool; Email string `validate:\"excluded_if=Enabled yes\"` }", false, "true or false");
        typeCheck(project, "package smoke\ntype Request struct { Name string `validate:\"\"`; Email *string `validate:\"excluded_if=Name \"` }", false, "use excluded_if=Name ''");
        typeCheck(project, "package smoke\ntype Request struct { Name string `validate:\"\"`; Email *string `validate:\"excluded_if=Name ''\"` }", false, null);
        typeCheck(project, "package smoke\ntype Request struct { Name string; Email *string `binding:\"excluded_if=Name \"` }", false, "trailing whitespace is not a value");
        typeCheck(project, "package smoke\ntype Request struct { Name string; Email *string `binding:\"excluded_if=Name ''\"` }", false, null);
        typeCheck(project, "package smoke\nimport \"example.com/utils\"\ntype UpdateToDoRequest struct { Title utils.PatchField[string] `json:\"title\" validate:\"excluded_if=description ''\"`; Description *string `json:\"description,omitempty\"`; Details map[string]any `json:\"details,omitempty\" validate:\"dive\"` }", false, "use Go field path 'Description'");
        typeCheck(project, "package smoke\nimport \"example.com/utils\"\ntype UpdateToDoRequest struct { Title utils.PatchField[string] `json:\"title\" validate:\"excluded_if=Description ''\"`; Description *string `json:\"description,omitempty\"`; Details map[string]any `json:\"details,omitempty\" validate:\"dive\"` }", false, null);
        typeCheck(project, "package smoke\ntype PatchField[T any] struct { Value T }; type UpdateToDoRequest struct { Title PatchField[string] `json:\"title\" validate:\"excluded_if=Description ''\"`; Description *string `json:\"description,omitempty\"`; Details map[string]any `json:\"details,omitempty\" validate:\"dive\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Mode string; Email string `validate:\"required_if=Mode 'New York'\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Email string `binding:\"required_without=Missing\"` }", false, "missing or invalid");
        typeCheck(project, "package smoke\ntype T struct { Mode string; Email string `validate:\"required_if=Mode x Mode y\"` }", false, "repeats reference");
        typeCheck(project, "package smoke\ntype T struct { Values []string; Email string `validate:\"required_if=Values 2\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Values []string; Email string `validate:\"required_if=Values many\"` }", false, "collection length");
        typeCheck(project, "package smoke\ntype Item struct { Code string }; type T struct { Items []Item; Confirm string `validate:\"eqcsfield=Items[0].Code\"` }", false, null);
        typeCheck(project, "package smoke\ntype Item struct { Code string }; type T struct { Items [2]Item; Confirm string `validate:\"eqcsfield=Items[3].Code\"` }", false, "missing or invalid");
        typeCheck(project, "package smoke\ntype Item struct { Code string }; type T struct { Items map[int]Item; Confirm string `validate:\"eqcsfield=Items[0].Code\"` }", false, null);
        typeCheck(project, "package smoke\ntype Item struct { Code string }; type T struct { Items map[int]Item; Confirm string `validate:\"eqcsfield=Items[wrong].Code\"` }", false, "missing or invalid");
        typeCheck(project, "package smoke\ntype Embed struct { Count int }; type T struct { Embed; Confirm int `validate:\"eqfield=Count\"` }", false, null);
        typeCheck(project, "package smoke\ntype A struct { Count int }; type B struct { A }; type C struct { A }; type T struct { B; C; Confirm int `validate:\"eqfield=Count\"` }", false, "ambiguous promoted");
        typeCheck(project, "package smoke\ntype A struct { N int }; type B struct { N int }; type T struct { First A; Second B `validate:\"eqfield=First\"` }", false, "matching struct types");
        typeCheck(project, "package smoke\ntype Item struct { ID int }; type T struct { Items []Item `validate:\"unique=Missing\"` }", false, "missing or invalid");
        typeCheck(project, "package smoke\ntype Item struct { ID int }; type T struct { Items []Item `validate:\"unique=ID\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Dynamic any; Confirm string `validate:\"eqcsfield=Dynamic.Code\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Unknown external.Model; Confirm string `validate:\"eqcsfield=Unknown.Code\"` }", false, null);
        String email = documentation(project, "N string `validate:\"required,em<caret>ail\"`");
        String required = documentation(project, "N string `validate:\"req<caret>uired,email\"`");
        check(email.contains("valid email address") && !email.contains("Built-in rules"), "Each rule hover has dedicated semantics");
        check(required.contains("present, non-empty") && !required.equals(email), "Adjacent required and email hovers differ");
        check(documentation(project, "N string `validate:\"email|uu<caret>id\"`").contains("UUID syntax"), "OR alternative has its own rule documentation");
        check(documentation(project, "N string `validate:\"required_if=Mode adv<caret>anced\"`").contains("Field Value"), "Hover over conditional parameter describes field/value pairs");
    }
    private void runTypedDocumentationChecks(Project project) {
        check(documentation(project, "N int `minL<caret>ength:\"1\"`") == null, "No hover documentation for unsupported schema key");
        check(documentation(project, "N *string `qu<caret>ery:\"q\"`") == null, "No hover documentation for pointer query parameter");
        String json = documentation(project, "N []int `js<caret>on:\"n\"`");
        check(json != null && json.contains("<code>omitempty</code>") && !json.contains("<code>string</code>"), "JSON hover options match slice datatype");
        check(documentation(project, "N []int `json:\"n,str<caret>ing\"`") == null, "No documentation on incompatible JSON option");
        check(documentation(project, "N []int \"json:\\\"n,str<caret>ing\\\"\"") == null, "Interpreted string option hover respects datatype");
        check(documentation(project, "N int `validate:\"em<caret>ail\"`") == null, "No documentation on incompatible validator rule");
        String rules = documentation(project, "N int `val<caret>idate:\"required\"`");
        check(rules != null && rules.contains("<code>numeric</code>") && !rules.contains("<code>email</code>"), "Validator key hover lists supported rules only");
        check(documentation(project, "N []string `validate:\"dive,em<caret>ail\"`").contains("<code>email</code>"), "Rule documentation uses dive element datatype");
        check(documentation(project, "N map[int]string `validate:\"dive,keys,em<caret>ail,endkeys\"`") == null, "Map key hover rejects string rule on integer key");
        check(documentation(project, "N map[int]string `validate:\"dive,keys,numeric,endkeys,em<caret>ail\"`") != null, "Endkeys hover restores map value datatype");
        check(documentation(project, "N unresolved.Type `minL<caret>ength:\"1\"`") != null, "Unresolved types retain documentation");
        PsiFile settingsFile = file(project, "N int `validate:\"email\"`");
        var profile = com.intellij.profile.codeInspection.InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
        var tool = (StructTagInspection) profile.getInspectionTool("GoTagWise", settingsFile).getTool();
        String previous = tool.customValidatorRules;
        try {
            tool.customValidatorRules = "email";
            check(documentation(project, "N int `validate:\"em<caret>ail\"`") != null, "Hover documentation respects profile custom rule override");
        } finally { tool.customValidatorRules = previous; }
    }
    private String documentation(Project project, String marked) {
        int relative = marked.indexOf("<caret>");
        PsiFile file = file(project, marked.replace("<caret>", ""));
        int offset = file.getText().indexOf('\n', file.getText().indexOf("type T")) + 1 + relative;
        var targets = new StructTagDocumentationProvider().documentationTargets(file, offset);
        if (targets.isEmpty()) return null;
        check(targets.get(0).computeDocumentation() != null, "Quick Documentation matches supported hover");
        return targets.get(0).computeDocumentationHint();
    }
    private void runDatatypeChecks(Project project) {
        for (String key : List.of("query", "path", "cookie")) {
            typeCheck(project, "package smoke\ntype T struct { Name *string `" + key + ":\"name\"` }", false, "does not support pointer");
        }
        typeCheck(project, "package smoke\ntype T struct { ID int `query:\"id\"`; Name *string `header:\"Name\"` }", false, "does not support pointer");
        typeCheck(project, "package smoke\ntype T struct { ID int `query:\"id\"`; Name *string `form:\"name\"` }", false, "does not support pointer");
        typeCheck(project, "package smoke\ntype T struct { Name *string `form:\"name\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name *string `header:\"Name\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name *string `header:\"Name\"` }", true, "does not support pointer");
        typeCheck(project, "package smoke\nimport _ \"github.com/danielgtaylor/huma/v2\"\ntype T struct { Name *string `form:\"name\"` }", false, "does not support pointer");
        typeCheck(project, "package smoke\nimport _ \"github.com/gin-gonic/gin\"\ntype T struct { Name *string `form:\"name\"` }", false, null);
        typeCheck(project, "package smoke\nimport _ \"github.com/danielgtaylor/huma/v2\"\ntype T struct { Name *string `header:\"Name\"` }\nfunc handler(input *T) {}", false, "does not support pointer");
        typeCheck(project, "package smoke\nimport _ \"github.com/danielgtaylor/huma/v2\"\ntype Output struct { Name *string `header:\"Name\"` }\nfunc handler() *Output { return nil }", false, null);
        typeCheck(project, "package smoke\ntype P = *string\ntype T struct { Name P `query:\"name\"` }", false, "does not support pointer");
        typeCheck(project, "package smoke\ntype P *string\ntype T struct { Name P `query:\"name\"` }", false, "does not support pointer");
        typeCheck(project, "package smoke\ntype S string\ntype T struct { Name S `minimum:\"1\"` }", false, "numeric field");
        typeCheck(project, "package smoke\ntype N int64\ntype T struct { Name N `bson:\",minsize\" minimum:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name []int `json:\",string\"` }", false, "only supported");
        typeCheck(project, "package smoke\ntype T struct { Name *int `json:\",string\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name int `minLength:\"1\"` }", false, "string field");
        typeCheck(project, "package smoke\ntype T struct { Name []byte `minLength:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name []byte `minItems:\"1\"` }", false, "slice or array");
        typeCheck(project, "package smoke\ntype T struct { Name map[int]string `yaml:\",inline\"` }", false, "string map keys");
        typeCheck(project, "package smoke\ntype T struct { Name *struct { N int } `yaml:\",inline\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name [][]byte `dynamodbav:\",binaryset\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name []string `dynamodbav:\",numberset\"` }", false, "incompatible element");
        typeCheck(project, "package smoke\nimport clock \"time\"\ntype T struct { Name clock.Time `dynamodbav:\",unixtime\"` }", false, null);
        typeCheck(project, "package smoke\ntype C struct{}\nfunc (C) MarshalJSON() ([]byte,error) { return nil,nil }\ntype T struct { Name C `minLength:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype C struct{}\nfunc (*C) Schema() {}\ntype T struct { Name C `minimum:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name unknown.Type `minimum:\"1\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Body *string `query:\"name\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { RawBody *string `query:\"name\"` }", false, null);
        typeCheck(project, "package smoke\ntype C struct{}\nfunc (*C) UnmarshalBSONValue() {}\ntype T struct { Name C `bson:\",minsize\"` }", false, null);
        typeCheck(project, "package smoke\ntype Embedded string\ntype T struct { *Embedded `query:\"name\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { Name *string `query:\"\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { N int `validate:\"email\"` }", false, "string field");
        typeCheck(project, "package smoke\ntype T struct { N []int `validate:\"dive,email\"` }", false, "string field");
        typeCheck(project, "package smoke\ntype T struct { N []string `validate:\"required,dive,email\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { N map[int]string `binding:\"dive,keys,numeric,endkeys,email\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { N bool `validate:\"min=1\"` }", false, "string, number");
        typeCheck(project, "package smoke\ntype T struct { N func() `json:\"n\"` }", false, "does not support");
        typeCheck(project, "package smoke\ntype T struct { N func() `json:\"-\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { N int `envPrefix:\"N_\"` }", false, "nested struct");
        typeCheck(project, "package smoke\ntype T struct { N int `gorm:\"embedded\"` }", false, "struct field");
        typeCheck(project, "package smoke\ntype T struct { N int `bun:\",array\"` }", false, "slice or array");
        typeCheck(project, "package smoke\nimport \"time\"\ntype T struct { N time.Time `validate:\"gte\"` }", false, null);
        typeCheck(project, "package smoke\ntype T struct { N []string `validate:\"unique=Name\"` }", false, "struct elements");
        typeCheck(project, "package smoke\ntype T struct { N int `validate:\"validateFn\"` }", false, "validation method");
        typeCheck(project, "package smoke\ntype N int\nfunc (N) Validate() error { return nil }\ntype T struct { Number N `validate:\"validateFn\"` }", false, null);
    }
    private void runTypedCompletionChecks(Project project) {
        check(!complete(project, "N int `minL<caret>`").contains("minLength"), "Numeric field hides string schema keys");
        check(complete(project, "N string `minL<caret>`").contains("minLength"), "String field offers string schema keys");
        check(!complete(project, "N *string `qu<caret>`").contains("query"), "Pointer field hides Huma query key before insertion");
        check(!complete(project, "N func() `js<caret>`").contains("json"), "Function field hides JSON key");
        check(!complete(project, "N int `validate:\"em<caret>\"`").contains("email"), "Numeric field hides email rule");
        check(complete(project, "N string `validate:\"em<caret>\"`").contains("email"), "String field offers email rule");
        check(!complete(project, "N []int `json:\"n,st<caret>\"`").contains("string"), "Slice hides JSON string option");
        check(complete(project, "N *int `json:\"n,st<caret>\"`").contains("string"), "Pointer scalar offers JSON string option");
        check(complete(project, "N []string `validate:\"dive,em<caret>\"`").contains("email"), "Dive completion uses string element");
        check(!complete(project, "N []int `validate:\"dive,em<caret>\"`").contains("email"), "Dive completion rejects numeric element email");
        check(complete(project, "N [][]string `validate:\"dive,di<caret>\"`").contains("dive"), "Nested collections offer another dive");
        check(!complete(project, "N map[int]string `validate:\"dive,keys,em<caret>\"`").contains("email"), "Map keys completion uses integer key type");
        check(complete(project, "N map[int]string `validate:\"dive,keys,numeric,endkeys,em<caret>\"`").contains("email"), "Endkeys restores string map values");
        check(!complete(project, "N int `hcl:\"n,la<caret>\"`").contains("label"), "HCL label option filtered by datatype");
        check(!complete(project, "N int `gorm:\"emb<caret>\"`").contains("embedded"), "GORM embedded option filtered by datatype");
        check(!complete(project, "N int `bun:\"n,ar<caret>\"`").contains("array"), "Bun array option filtered by datatype");
        check(!complete(project, "N int `env:\"N,in<caret>\"`").contains("init"), "Env init requires pointer in completion");
        check(!complete(project, "N int `xml:\"n,in<caret>\"`").contains("innerxml"), "XML mode filtered by datatype");
        var context = new StructTagTemplateContext();
        PsiFile invalidTemplate = file(project, "N int `stminLength`");
        check(!context.isInContext(TemplateActionContext.expanding(invalidTemplate, invalidTemplate.getText().indexOf("stminLength") + 11)), "Incompatible live template does not expand");
        Set<String> forwarded = complete(project, "N []int `json:\"n,<caret>\"`", List.of("string", "omitempty"));
        check(!forwarded.contains("string") && forwarded.contains("omitempty"), "Other contributors cannot reintroduce incompatible options");
        // Completion and diagnostics read custom overrides from the same inspection profile.
        PsiFile profileFile = file(project, "N int `validate:\"email\"`");
        var profile = com.intellij.profile.codeInspection.InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
        var tool = (StructTagInspection) profile.getInspectionTool("GoTagWise", profileFile).getTool();
        String saved = tool.customValidatorRules;
        try {
            tool.customValidatorRules = "email,custom_numeric";
            check(complete(project, "N int `validate:\"em<caret>\"`").contains("email"), "Custom built-in override retained in completion");
            check(complete(project, "N int `validate:\"custom_<caret>\"`").contains("custom_numeric"), "Custom rule names are completed");
        } finally { tool.customValidatorRules = saved; }
    }
    private void typeCheck(Project project, String source, boolean forceHuma, String expected) {
        PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("types.go", GoLanguage.INSTANCE, source);
        var holder = new ProblemsHolder(InspectionManager.getInstance(project), file, false);
        var inspection = new StructTagInspection();
        inspection.assumeHumaParameters = forceHuma;
        var visitor = inspection.buildVisitor(holder, false);
        for (var tag : PsiTreeUtil.findChildrenOfType(file, GoTag.class)) tag.accept(visitor);
        var messages = holder.getResults().stream().map(ProblemDescriptor::getDescriptionTemplate).toList();
        check(expected == null ? messages.isEmpty() : messages.stream().anyMatch(m -> m.contains(expected)), "Datatype inspection: " + source + "; got " + messages);
    }
    private Set<String> complete(Project project, String marked) {
        return complete(project, marked, List.of());
    }
    private Set<String> complete(Project project, String marked, List<String> downstream) {
        int relative = marked.indexOf("<caret>");
        PsiFile file = file(project, marked.replace("<caret>", ""));
        int offset = file.getText().indexOf('\n', file.getText().indexOf("type T")) + 1 + relative;
        EditorFactory factory = EditorFactory.getInstance();
        Editor editor = factory.createEditor(Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(file)), project);
        try {
            var contributor = new StructTagCompletionContributor();
            Set<String> values = new HashSet<>();
            var params = new CompletionParameters(Objects.requireNonNull(file.findElementAt(offset)), file, CompletionType.BASIC, offset, 1, editor, () -> false);
            contributor.fillCompletionVariants(params, new CapturingResult(PrefixMatcher.ALWAYS_TRUE, contributor, values, downstream));
            return values;
        } finally { factory.releaseEditor(editor); }
    }
    private void runFix(Project project) {
        PsiFile file = file(project, "Name string `json:\"name,omitemtpy\"`");
        var holder = new ProblemsHolder(InspectionManager.getInstance(project), file, false);
        GoTag tag = Objects.requireNonNull(PsiTreeUtil.findChildOfType(file, GoTag.class));
        tag.accept(new StructTagInspection().buildVisitor(holder, false));
        check(holder.getResults().size() == 1, "IDE visitor produces typo diagnostic");
        ProblemDescriptor descriptor = holder.getResults().get(0);
        check(descriptor.getFixes() != null && descriptor.getFixes().length == 1, "Typo quick fix registered");
        descriptor.getFixes()[0].applyFix(project, descriptor);
        check(file.getText().contains("json:\"name,omitempty\""), "Quick fix changes correct Go PSI range");
        var docs = new StructTagDocumentationProvider();
        var pointer = docs.documentationTargets(file, file.getText().indexOf("json")).get(0).createPointer();
        Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(file));
        int start = document.getText().indexOf("json");
        document.replaceString(start, start + 4, "query");
        PsiDocumentManager.getInstance(project).commitDocument(document);
        check(pointer.dereference() == null, "Documentation pointer invalidates when the tag key changes");
        PsiFile typed = file(project, "Name string `minLength:\"1\"`");
        var typedPointer = docs.documentationTargets(typed, typed.getText().indexOf("minLength")).get(0).createPointer();
        Document typedDocument = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(typed));
        int typeStart = typedDocument.getText().indexOf("string");
        typedDocument.replaceString(typeStart, typeStart + 6, "int");
        PsiDocumentManager.getInstance(project).commitDocument(typedDocument);
        check(typedPointer.dereference() == null, "Documentation invalidates when field datatype becomes incompatible");
    }
    private static final class CapturingResult extends CompletionResultSet {
        private final Set<String> values;
        private final List<String> downstream;
        CapturingResult(PrefixMatcher matcher, CompletionContributor contributor, Set<String> values, List<String> downstream) { super(matcher, result -> values.add(result.getLookupElement().getLookupString()), contributor); this.values = values; this.downstream = downstream; }
        public void addElement(LookupElement value) { if (getPrefixMatcher().prefixMatches(value)) values.add(value.getLookupString()); }
        public CompletionResultSet withPrefixMatcher(PrefixMatcher matcher) { return new CapturingResult(matcher, contributor, values, downstream); }
        public CompletionResultSet withPrefixMatcher(String prefix) {
            return withPrefixMatcher(new PrefixMatcher(prefix) {
                public boolean prefixMatches(String value) { return value.startsWith(getPrefix()); }
                public PrefixMatcher cloneWithPrefix(String value) { return PrefixMatcher.ALWAYS_TRUE; }
            });
        }
        public CompletionResultSet withRelevanceSorter(CompletionSorter sorter) { return this; }
        public void addLookupAdvertisement(String text) {}
        public CompletionResultSet caseInsensitive() { return this; }
        public void restartCompletionOnPrefixChange(ElementPattern<String> pattern) {}
        public void restartCompletionWhenNothingMatches() {}
        public void runRemainingContributors(CompletionParameters parameters, com.intellij.util.Consumer<? super CompletionResult> consumer) {
            for (String word : downstream) consumer.consume(CompletionResult.wrap(com.intellij.codeInsight.lookup.LookupElementBuilder.create(word), PrefixMatcher.ALWAYS_TRUE, CompletionService.getCompletionService().emptySorter()));
        }
    }
}

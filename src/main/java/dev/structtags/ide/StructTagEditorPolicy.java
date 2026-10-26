package dev.structtags.ide;

import com.goide.psi.GoFieldDeclaration;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import dev.structtags.core.FieldType;
import java.util.*;

record StructTagEditorPolicy(FieldType type, boolean humaInput, Set<String> validators, Set<String> sanitizers) {
    static StructTagEditorPolicy forField(GoFieldDeclaration field) {
        var wrapper = InspectionProjectProfileManager.getInstance(field.getProject()).getCurrentProfile().getInspectionTool("GoTagWise", field);
        StructTagInspection inspection = wrapper != null && wrapper.getTool() instanceof StructTagInspection tool ? tool : new StructTagInspection();
        return new StructTagEditorPolicy(GoFieldTypes.of(field), GoFieldTypes.humaInput(field, inspection.assumeHumaParameters), words(inspection.customValidatorRules), words(inspection.customSanitizers));
    }
    private static Set<String> words(String csv) {
        Set<String> out = new LinkedHashSet<>();
        for (String word : csv.split(",")) if (!word.isBlank()) out.add(word.trim());
        return Collections.unmodifiableSet(out);
    }
    Set<String> custom(String key) { return key.equals("conform") ? sanitizers : validators; }
}

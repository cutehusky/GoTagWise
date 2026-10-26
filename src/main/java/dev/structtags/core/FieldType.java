package dev.structtags.core;

import java.util.Set;

/** Conservative resolved Go type information. UNKNOWN is never treated as incompatible. */
public record FieldType(Kind kind, String name, FieldType element, FieldType key, Set<String> methods) {
    public enum Kind { UNKNOWN, POINTER, STRING, BOOLEAN, INTEGER, UNSIGNED, FLOAT, COMPLEX, STRUCT, MAP, SLICE, ARRAY, INTERFACE, FUNCTION, CHANNEL }
    public static final FieldType UNKNOWN = of(Kind.UNKNOWN, "unknown");
    public static FieldType of(Kind kind, String name) { return new FieldType(kind, name, null, null, Set.of()); }
    public static FieldType container(Kind kind, FieldType element, FieldType key) { return new FieldType(kind, kind.name().toLowerCase(), element, key, Set.of()); }
    public FieldType withMethods(Set<String> names) { return new FieldType(kind, name, element, key, Set.copyOf(names)); }
    public FieldType dereference() {
        FieldType result = this;
        for (int i = 0; i < 32 && result.kind == Kind.POINTER && result.element != null; i++) result = result.element;
        return result;
    }
    public boolean numeric() { return kind == Kind.INTEGER || kind == Kind.UNSIGNED || kind == Kind.FLOAT; }
    public boolean collection() { return kind == Kind.SLICE || kind == Kind.ARRAY; }
    public boolean unknown() { return kind == Kind.UNKNOWN || kind == Kind.INTERFACE; }
    public boolean has(String method) { return methods.contains(method) || kind == Kind.POINTER && element != null && element.has(method); }
    public boolean customSchema() { return has("Schema") || has("MarshalJSON") || has("UnmarshalJSON") || has("MarshalText") || has("UnmarshalText"); }
}

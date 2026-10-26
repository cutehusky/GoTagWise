package dev.structtags.core;

import java.util.*;

/** Shared vocabulary for completion, templates and inspection. Unknown tag keys are legal Go. */
public final class TagCatalog {
    public enum Kind { TEXT, NAME_OPTIONS, RULES, DIRECTIVES, BOOLEAN, NUMBER, NONNEGATIVE, POSITIVE }
    public record Spec(String key, String library, Kind kind, String options, String example) {
        public List<String> words() { return options.isEmpty() ? List.of() : List.of(options.split(" ")); }
    }
    private static final Map<String, Spec> TAGS = new LinkedHashMap<>();
    private static void add(String key, String library, Kind kind, String options, String example) {
        TAGS.put(key, new Spec(key, library, kind, options, example));
    }
    static {
        add("json", "encoding/json", Kind.NAME_OPTIONS, "omitempty omitzero string", "field");
        add("bson", "MongoDB BSON", Kind.NAME_OPTIONS, "omitempty minsize truncate inline", "field");
        add("dynamodbav", "AWS attributevalue", Kind.NAME_OPTIONS, "omitempty omitemptyelem nullempty nullemptyelem string binaryset numberset stringset unixtime", "field");
        add("env", "caarlos0/env/v11", Kind.NAME_OPTIONS, "required notEmpty file unset expand init", "FIELD");
        for (String key : List.of("envDefault", "envPrefix", "envSeparator", "envKeyValSeparator"))
            add(key, "caarlos0/env/v11", Kind.TEXT, "", key.contains("Separator") ? "," : "value");
        add("xml", "encoding/xml", Kind.NAME_OPTIONS, "attr chardata cdata innerxml comment any omitempty", "field");
        add("yaml", "yaml.v3", Kind.NAME_OPTIONS, "omitempty flow inline", "field");
        add("msgpack", "vmihailenco/msgpack", Kind.NAME_OPTIONS, "omitempty asArray inline noinline", "field");
        add("toml", "BurntSushi/toml · pelletier/go-toml", Kind.NAME_OPTIONS, "omitempty omitzero", "field");
        add("comment", "pelletier/go-toml", Kind.TEXT, "", "description");
        for (String key : List.of("commented", "multiline", "literal")) add(key, "pelletier/go-toml", Kind.BOOLEAN, "true false", "true");
        add("redis", "go-redis/v9 · redigo/redis", Kind.NAME_OPTIONS, "omitempty", "field");
        add("hcl", "HashiCorp gohcl · alecthomas/hcl", Kind.NAME_OPTIONS, "attr block label remain body optional key", "field");
        for (String key : List.of("help", "default", "enum")) add(key, "HCL · Huma v2", Kind.TEXT, "", "value");
        for (String key : List.of("form", "query", "header", "uri", "path", "cookie"))
            add(key, key.equals("header") ? "Gin · Huma v2" : Set.of("query", "path", "cookie").contains(key) ? "Huma v2" : "Gin", Kind.NAME_OPTIONS, key.equals("form") ? "default= collection_format=" : key.equals("query") ? "explode" : "", "field");
        add("collection_format", "Gin binding", Kind.TEXT, "csv ssv tsv pipes multi", "csv");
        add("time_format", "Gin binding", Kind.TEXT, "unix unixmilli unixmicro unixnano", "2006-01-02");
        add("time_utc", "Gin binding", Kind.BOOLEAN, "true false", "true");
        add("time_location", "Gin binding", Kind.TEXT, "UTC Local", "UTC");
        add("binding", "Gin binding / validator/v10", Kind.RULES, validatorWords(), "required");
        add("validate", "go-playground/validator/v10", Kind.RULES, validatorWords(), "required");
        add("conform", "leebenson/conform", Kind.RULES, "trim ltrim rtrim lower upper title camel snake slug ucfirst name email num !num alpha !alpha !html !js", "trim,lower");
        add("gorm", "GORM", Kind.DIRECTIVES,
            "column: type: serializer: size: primaryKey unique default: precision: scale: not_null autoIncrement autoIncrementIncrement: embedded embeddedPrefix: autoCreateTime autoUpdateTime index uniqueIndex check: <- -> - foreignKey: references: polymorphic: polymorphicValue: many2many: joinForeignKey: joinReferences: constraint:", "column:field");
        add("bun", "Bun", Kind.NAME_OPTIONS,
            "pk autoincrement notnull nullzero unique unique: type: default: scanonly array json_use_number msgpack soft_delete rel: join: m2m: polymorphic: on_delete: on_update: embed: extend table: alias: select:", "field");
        for (String key : List.of("doc", "format", "const", "pattern", "patternDescription", "example", "dependentRequired", "contentType"))
            add(key, "Huma v2", Kind.TEXT, "", "value");
        for (String key : List.of("required", "readOnly", "writeOnly", "deprecated", "hidden", "uniqueItems", "nullable"))
            add(key, "Huma v2", Kind.BOOLEAN, "true false", "true");
        for (String key : List.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum"))
            add(key, "Huma v2", Kind.NUMBER, "", "0");
        add("multipleOf", "Huma v2", Kind.POSITIVE, "", "1");
        for (String key : List.of("minLength", "maxLength", "minItems", "maxItems", "minProperties", "maxProperties"))
            add(key, "Huma v2", Kind.NONNEGATIVE, "", "1");
    }
    private static String validatorWords() {
        return "required required_if= required_unless= required_with= required_with_all= required_without= required_without_all= excluded_if= excluded_unless= excluded_with= excluded_with_all= excluded_without= excluded_without_all= isdefault omitempty omitnil dive keys endkeys structonly nostructlevel - " +
            "eq= ne= gt= gte= lt= lte= min= max= len= oneof= oneofci= eqfield= nefield= gtfield= gtefield= ltfield= ltefield= eqcsfield= necsfield= gtcsfield= gtecsfield= ltcsfield= ltecsfield= fieldcontains= fieldexcludes= " +
            "alpha alphanum alphaunicode alphanumunicode ascii printascii boolean number numeric hexadecimal hexcolor rgb rgba hsl hsla iscolor email url uri http_url urn hostname hostname_rfc1123 fqdn hostname_port lowercase uppercase startswith= endswith= startsnotwith= endsnotwith= contains= containsany= containsrune= excludes= excludesall= excludesrune= " +
            "uuid uuid3 uuid4 uuid5 uuid_rfc4122 uuid3_rfc4122 uuid4_rfc4122 uuid5_rfc4122 ulid md4 md5 sha256 sha384 sha512 ripemd128 ripemd160 tiger128 tiger160 tiger192 base64 base64url base64rawurl json jwt html html_encoded url_encoded " +
            "ip ipv4 ipv6 ip_addr ip4_addr ip6_addr cidr cidrv4 cidrv6 tcp_addr tcp4_addr tcp6_addr udp_addr udp4_addr udp6_addr unix_addr mac e164 latitude longitude ssn isbn isbn10 isbn13 credit_card luhn_checksum bic btc_addr btc_addr_bech32 eth_addr " +
            "datetime= timezone iso3166_1_alpha2 iso3166_1_alpha3 iso3166_1_alpha_numeric iso3166_2 iso4217 iso4217_numeric bcp47_language_tag postcode_iso3166_alpha2= postcode_iso3166_alpha2_field= country_code eu_country_code semver cve mongodb cron file dir filepath dirpath image unique= validateFn= spicedb=";
    }
    public static Collection<Spec> all() { return Collections.unmodifiableCollection(TAGS.values()); }
    public static Spec get(String key) { return TAGS.get(key); }
    public static String name(String field, String style) {
        String snake = field.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        if (style.equals("upper")) return snake.toUpperCase(Locale.ROOT);
        if (style.equals("camel")) {
            int n = 0;
            while (n < field.length() && Character.isUpperCase(field.charAt(n))) n++;
            if (n > 1 && n < field.length()) n--;
            return field.substring(0, n).toLowerCase(Locale.ROOT) + field.substring(n);
        }
        return snake;
    }
}

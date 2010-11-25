package org.exist.security;

/**
 *
 * @author aretter
 */
public enum AXSchemaType {

    ALIAS_USERNAME("http://axschema.org/namePerson/friendly", "Alias"),
    FIRSTNAME("http://axschema.org/namePerson/first", "FirstName"),
    LASTNAME("http://axschema.org/namePerson/last", "LastName"),
    FULLNAME("http://axschema.org/namePerson", "FullName"),
    EMAIL("http://axschema.org/contact/email", "Email"),
    COUNTRY("http://axschema.org/contact/country/home", "Country"),
    LANGUAGE("http://axschema.org/pref/language", "Language"),
    TIMEZONE("http://axschema.org/pref/timezone", "Timezone");

    private final String namespace;
    private final String alias;

    AXSchemaType(String namespace, String alias) {
        this.namespace = namespace;
        this.alias = alias;
    }

    public static AXSchemaType valueOfNamespace(String namespace) {
        for(AXSchemaType axSchemaType : AXSchemaType.values()) {
            if(axSchemaType.getNamespace().equals(namespace)) {
                return axSchemaType;
            }
        }
        return null;
    }

    public static AXSchemaType valueOfAlias(String alias) {
        for(AXSchemaType axSchemaType : AXSchemaType.values()) {
            if(axSchemaType.getAlias().equals(alias)) {
                return axSchemaType;
            }
        }
        return null;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAlias() {
        return alias;
    }
}
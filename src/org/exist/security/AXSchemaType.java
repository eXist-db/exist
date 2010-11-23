package org.exist.security;

/**
 *
 * @author aretter
 */
public enum AXSchemaType {

    FIRTSNAME("http://axschema.org/namePerson/first"),
    LASTNAME("http://axschema.org/namePerson/last"),
    FULLNAME("http://axschema.org/namePerson"),
    EMAIL("http://axschema.org/contact/email"),
    COUNTRY("http://axschema.org/contact/country/home"),
    LANGUAGE("http://axschema.org/pref/language"),
    TIMEZONE("http://axschema.org/pref/timezone");

    private final String namespace;

    AXSchemaType(String namespace) {
        this.namespace = namespace;
    }

    public static AXSchemaType valueOfNamespace(String namespace) {
        for(AXSchemaType axSchemaType : AXSchemaType.values()) {
            if(axSchemaType.getNamespace().equals(namespace)) {
                return axSchemaType;
            }
        }
        return null;
    }

    public String getNamespace() {
        return namespace;
    }
}
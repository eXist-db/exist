/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.security;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public enum AXSchemaType implements SchemaType {

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

    AXSchemaType(final String namespace, final String alias) {
        this.namespace = namespace;
        this.alias = alias;
    }
    
    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getAlias() {
        return alias;
    }
    
    public static AXSchemaType valueOfNamespace(final String namespace) {
        for(final AXSchemaType axSchemaType : AXSchemaType.values()) {
            if(axSchemaType.getNamespace().equals(namespace)) {
                return axSchemaType;
            }
        }
        return null;
    }

    public static AXSchemaType valueOfAlias(final String alias) {
        for(final AXSchemaType axSchemaType : AXSchemaType.values()) {
            if(axSchemaType.getAlias().equals(alias)) {
                return axSchemaType;
            }
        }
        return null;
    }
}
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
public enum EXistSchemaType implements SchemaType {
    DESCRIPTION("http://exist-db.org/security/description", "Description");
    
    private final String namespace;
    private final String alias;
    
    EXistSchemaType(final String namespace, final String alias) {
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
    
    public static EXistSchemaType valueOfNamespace(final String namespace) {
        for(final EXistSchemaType existSchemaType : EXistSchemaType.values()) {
            if(existSchemaType.getNamespace().equals(namespace)) {
                return existSchemaType;
            }
        }
        return null;
    }

    public static EXistSchemaType valueOfAlias(final String alias) {
        for(final EXistSchemaType existSchemaType : EXistSchemaType.values()) {
            if(existSchemaType.getAlias().equals(alias)) {
                return existSchemaType;
            }
        }
        return null;
    }
}

package org.exist.security.realm.ldap.xquery;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 *
 * http://exist-db.org/xquery/versioning
 * 
 * @author Adam Retter <adam@exist-db.org>
 */

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-11 The eXist-db Project
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
 * $Id$
 */
public class LDAPModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/security/ldap/xquery";
    public final static String PREFIX = "ldap";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";
    
    public static final FunctionDef[] functions = {
        new FunctionDef(AccountFunctions.signatures[0], AccountFunctions.class)
    };

    public LDAPModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }
    
    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "A module for maniuplating LDAP security";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
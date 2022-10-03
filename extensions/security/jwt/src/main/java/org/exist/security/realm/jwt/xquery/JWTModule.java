/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security.realm.jwt.xquery;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

public class JWTModule extends AbstractInternalModule {

    public final static String NAMESPACE = "http://exist-db.org/security/jwt/xquery";

    public final static String PREFIX = "jwt";

    private final static String DESCRIPTION = "JSON Web Token authorization module.";

    private final static String RELEASED_IN_VERSION = "eXist-5.3.0";

    private final static FunctionDef[] functions = functionDefs(
            functionDefs(JWTFunctions.class, JWTFunctions.signatures[0])
    );

    public JWTModule(Map<String, List<?>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
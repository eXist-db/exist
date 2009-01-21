/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
package org.exist.versioning.xquery;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

public class VersioningModule extends AbstractInternalModule {


    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/versioning";

    public static final String PREFIX = "version";

    public static final FunctionDef[] functions = {
        new FunctionDef(PatchFunction.signatures[0], PatchFunction.class),
        new FunctionDef(PatchFunction.signatures[1], PatchFunction.class),
        new FunctionDef(DiffFunction.signature, DiffFunction.class)
    };

    public VersioningModule() {
        super(functions);
    }

    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getDescription() {
        return "Versioning functions.";
    }
}

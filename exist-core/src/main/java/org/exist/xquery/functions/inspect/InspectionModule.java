/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2020 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery.functions.inspect;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

public class InspectionModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/inspection";

    public final static String PREFIX = "inspect";

    public final static String RELEASE = "2.0";

    public final static FunctionDef[] functions = functionDefs(
        functionDefs(InspectFunction.class,
                InspectFunction.SIGNATURE),
        functionDefs(InspectModule.class,
                InspectModule.FNS_INSPECT_MODULE,
                InspectModule.FNS_INSPECT_MODULE_URI),
        functionDefs(ModuleFunctions.class,
                ModuleFunctions.FNS_MODULE_FUNCTIONS_CURRENT,
                ModuleFunctions.FNS_MODULE_FUNCTIONS_OTHER,
                ModuleFunctions.FNS_MODULE_FUNCTIONS_OTHER_URI
        )
    );

    public InspectionModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, true);
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
        return "Functions for inspecting XQuery modules and functions";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE;
    }

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, variableParamTypes);
    }
}

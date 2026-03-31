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
package org.exist.xquery.functions.math;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 *  eXist module for mathematical operations.
 *
 * @author Dannes Wessels
 * @author ljo
 */
public class MathModule extends AbstractInternalModule {
    
    public static final String NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/math";
    
    public static final String PREFIX = "math";
    public static final String INCLUSION_DATE = "2012-12-05";
    public static final String RELEASED_IN_VERSION = "eXist-2.0";

    private static final FunctionDef[] functions = {
        
        new FunctionDef(OneParamFunctions.FNS_ACOS, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_ASIN, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_ATAN, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_COS, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_EXP, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_EXP10, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_LOG, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_LOG10, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_SIN, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_SQRT, OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.FNS_TAN, OneParamFunctions.class),
        
        new FunctionDef(NoParamFunctions.FNS_PI, NoParamFunctions.class),
        
        new FunctionDef(TwoParamFunctions.FNS_ATAN2, TwoParamFunctions.class),
        new FunctionDef(TwoParamFunctions.FNS_POW, TwoParamFunctions.class)
    };
    
    public MathModule(Map<String, List<?>> parameters) {
        super(functions, parameters);
    }
    
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }
    
    public String getDefaultPrefix() {
        return PREFIX;
    }
    
    public String getDescription() {
        return "A module containing functions for common mathematical operations.";
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}

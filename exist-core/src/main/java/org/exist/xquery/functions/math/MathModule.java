/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.xquery.functions.math;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 *  eXist module for mathematical operations.
 *
 * @author Dannes Wessels
 * @author ljo
 */
public class MathModule extends AbstractInternalModule {
    
    public final static String NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/math";
    
    public final static String PREFIX = "math";
    public final static String INCLUSION_DATE = "2012-12-05";
    public final static String RELEASED_IN_VERSION = "eXist-2.0";

    private final static FunctionDef functions[] = {
        
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
    
    public MathModule(Map<String, List<? extends Object>> parameters) {
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

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 */
package org.exist.xquery.modules.math;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 *  eXist module for mathematical operations.
 *
 * @author Dannes Wessels
 */
public class MathModule extends AbstractInternalModule {
    
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/math";
    
    public final static String PREFIX = "math";
    
    private final static FunctionDef functions[] = {
        new FunctionDef(OneParamFunctions.signature[0], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[1], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[2], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[3], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[4], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[5], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[6], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[7], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[8], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[9], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[10], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[11], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[12], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[13], OneParamFunctions.class),
        new FunctionDef(OneParamFunctions.signature[14], OneParamFunctions.class),
        new FunctionDef(NoParamFunctions.signature[0], NoParamFunctions.class),
        new FunctionDef(NoParamFunctions.signature[1], NoParamFunctions.class),
        new FunctionDef(NoParamFunctions.signature[2], NoParamFunctions.class),
        new FunctionDef(TwoParamFunctions.signature[0], TwoParamFunctions.class),
        new FunctionDef(TwoParamFunctions.signature[1], TwoParamFunctions.class)
    };
    
    public MathModule() {
        super(functions);
    }
    
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }
    
    public String getDefaultPrefix() {
        return PREFIX;
    }
    
    public String getDescription() {
        return "Functions for mathematical operations.";
    }
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.xquery.modules.math;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Dannes Wessels
 */
public class MathModule extends AbstractInternalModule {
    
    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/math";
    
    public final static String PREFIX = "math";
    
    private final static FunctionDef functions[] = {
        new FunctionDef(SimpleFunctions.signature[0], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[1], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[2], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[3], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[4], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[5], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[6], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[7], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[8], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[9], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[10], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[11], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[12], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[13], SimpleFunctions.class),
        new FunctionDef(SimpleFunctions.signature[14], SimpleFunctions.class),
        new FunctionDef(Constants.signature[0], Constants.class),
        new FunctionDef(Constants.signature[1], Constants.class),
        new FunctionDef(ComplexFunctions.signature[0], ComplexFunctions.class),
        new FunctionDef(ComplexFunctions.signature[1], ComplexFunctions.class)
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
        return "Module containing mathematical functions.";
    }
}

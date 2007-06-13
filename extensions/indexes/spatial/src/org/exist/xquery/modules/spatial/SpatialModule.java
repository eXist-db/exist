/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 *  
 *  @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
package org.exist.xquery.modules.spatial;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

public class SpatialModule extends AbstractInternalModule {

	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/spatial";
	
	public static final String PREFIX = "spatial";

    public static final FunctionDef[] functions = {
    	new FunctionDef(FunSpatialSearch.signatures[0], FunSpatialSearch.class),
    	new FunctionDef(FunSpatialSearch.signatures[1], FunSpatialSearch.class),
    	new FunctionDef(FunSpatialSearch.signatures[2], FunSpatialSearch.class),
    	new FunctionDef(FunSpatialSearch.signatures[3], FunSpatialSearch.class),
    	new FunctionDef(FunSpatialSearch.signatures[4], FunSpatialSearch.class),
    	new FunctionDef(FunSpatialSearch.signatures[5], FunSpatialSearch.class),
    	new FunctionDef(FunSpatialSearch.signatures[6], FunSpatialSearch.class),
    	new FunctionDef(FunSpatialSearch.signatures[7], FunSpatialSearch.class),    	
    	new FunctionDef(FunGeometricProperties.signatures[0], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[1], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[2], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[3], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[4], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[5], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[6], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[7], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[8], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[9], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[10], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[11], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[12], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[13], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[14], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[15], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[16], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[17], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[18], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[18], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[19], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[20], FunGeometricProperties.class),
    	new FunctionDef(FunGeometricProperties.signatures[21], FunGeometricProperties.class), 
    	new FunctionDef(FunGeometricProperties.signatures[22], FunGeometricProperties.class),    
    	new FunctionDef(FunGMLProducers.signatures[0], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[1], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[2], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[3], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[4], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[5], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[6], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[7], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[8], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[9], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[10], FunGMLProducers.class),
    	new FunctionDef(FunGMLProducers.signatures[11], FunGMLProducers.class)    	
    };
	
	public SpatialModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "Functions to perform spatial operations on GML 2D geometries.";
	}
}

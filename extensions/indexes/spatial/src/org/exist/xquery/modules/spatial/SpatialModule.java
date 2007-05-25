/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
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
    	new FunctionDef(FunSpatialSearch.signatures[7], FunSpatialSearch.class)    	
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

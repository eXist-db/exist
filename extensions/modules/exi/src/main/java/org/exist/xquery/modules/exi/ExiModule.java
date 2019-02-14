/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.xquery.modules.exi;

import java.util.List;
import java.util.Map;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * XQuery Extension module for Efficient XML Interchange (EXI) processing.
 *
 * @author Rob Walpole
 * @version 1.0
 */
public class ExiModule extends AbstractInternalModule {
	
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/exi";
	
	public final static String PREFIX = "exi";
    public final static String INCLUSION_DATE = "2011-06-09";
    public final static String RELEASED_IN_VERSION = "eXist-2.0";
    
    private final static FunctionDef[] functions = {
		new FunctionDef(EncodeExiFunction.signatures[0], EncodeExiFunction.class),
		new FunctionDef(EncodeExiFunction.signatures[1], EncodeExiFunction.class),
		new FunctionDef(DecodeExiFunction.signatures[0], DecodeExiFunction.class),
		new FunctionDef(DecodeExiFunction.signatures[1], DecodeExiFunction.class)
	};
	
	public ExiModule(Map<String, List<?>> parameters) {
		super(functions, parameters);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for working with the EXI (Efficient XML Interchange) format.";
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
    
}
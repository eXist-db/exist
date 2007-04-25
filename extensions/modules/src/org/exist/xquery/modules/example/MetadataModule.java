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
 *  
 *  $Id$
 */
package org.exist.xquery.modules.metadata;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class MetadataModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/metadata";
	
	public final static String PREFIX = "metadata";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(MetadataFunction.signature, MetadataFunction.class)
	};
	
	public MetadataModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "Metadata module";
	}
}

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
 *  $Id: ExampleModule.java 1173 2005-04-20 11:15:18Z wolfgang_m $
 */
package org.exist.xquery.modules.compression;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * XQuery Extension module for compression and de-compression functions
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @version 1.0
 */
public class CompressionModule extends AbstractInternalModule
{

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/compression";
	
	public final static String PREFIX = "compression";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(ZipFunction.signature, ZipFunction.class)
	};
	
	public CompressionModule()
	{
		super(functions);
	}

	public String getNamespaceURI()
	{
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix()
	{
		return PREFIX;
	}

	public String getDescription()
	{
		return "Compression and De-Compression functions";
	}
}

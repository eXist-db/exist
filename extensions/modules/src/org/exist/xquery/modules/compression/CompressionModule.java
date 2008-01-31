/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
		new FunctionDef(ZipFunction.signatures[0], ZipFunction.class),
        new FunctionDef(ZipFunction.signatures[1], ZipFunction.class)
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

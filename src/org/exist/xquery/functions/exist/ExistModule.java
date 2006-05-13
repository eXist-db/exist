/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery.functions.exist;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * Module to provide information and function directly related to a running eXist server
 * 
 * @author Adam Retter (adam.retter@devon.gov.uk)
 * 
 * TODO: add get-uptime()
 * TODO: add count-sessions()
 */

public class ExistModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/exist";
	public final static String PREFIX = "exist";
	
	public final static FunctionDef[] functions = {
		
		new FunctionDef(CountInstances.countInstancesMax, CountInstances.class),
		new FunctionDef(CountInstances.countInstancesActive, CountInstances.class),
		new FunctionDef(CountInstances.countInstancesAvailable, CountInstances.class),
		new FunctionDef(GetMemory.getMemoryMax, GetMemory.class),
		new FunctionDef(GetMemory.getMemoryTotal, GetMemory.class),
		new FunctionDef(GetMemory.getMemoryFree, GetMemory.class),
		new FunctionDef(GetVersion.signature, GetVersion.class),
		new FunctionDef(GetBuild.signature, GetBuild.class),
		new FunctionDef(Shutdown.signatures[0], Shutdown.class),
		new FunctionDef(Shutdown.signatures[1], Shutdown.class)
		
	};
    
	public ExistModule() throws XPathException
	{
		super(functions);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "Functions to retrieve information about eXist";
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}
}

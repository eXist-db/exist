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
 
package org.exist.xquery.functions.xmldb;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ModuleImpl extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/xmldb";
	
	public final static String PREFIX = "xmldb";
	
	public final static FunctionDef[] functions = {
		new FunctionDef(XMLDBCollection.signature, XMLDBCollection.class),
		new FunctionDef(XMLDBCreateCollection.signature, XMLDBCreateCollection.class),
		new FunctionDef(XMLDBRegisterDatabase.signature, XMLDBRegisterDatabase.class),
		new FunctionDef(XMLDBStore.signature, XMLDBStore.class),
		new FunctionDef(XMLDBAuthenticate.signature, XMLDBAuthenticate.class),
		new FunctionDef(XMLDBXUpdate.signature, XMLDBXUpdate.class),
		new FunctionDef(XMLDBRemove.signature, XMLDBRemove.class),
		new FunctionDef(XMLDBHasLock.signature, XMLDBHasLock.class),
		new FunctionDef(XMLDBCreated.signature, XMLDBCreated.class),
		new FunctionDef(XMLDBLastModified.signature, XMLDBLastModified.class),
		new FunctionDef(XMLDBPermissions.signature, XMLDBPermissions.class),
		new FunctionDef(XMLDBGroup.signature, XMLDBGroup.class),
		new FunctionDef(XMLDBOwner.signature, XMLDBOwner.class)
	};
	
	public ModuleImpl() {
		super(functions);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}

}

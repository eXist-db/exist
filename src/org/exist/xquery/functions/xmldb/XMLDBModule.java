/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  Some modifications Copyright (C) 2004 Luigi P. Bai
 *  finder@users.sf.net
 *  Licensed as below under the LGPL.
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
public class XMLDBModule extends AbstractInternalModule {

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
		new FunctionDef(XMLDBOwner.signature, XMLDBOwner.class),
		new FunctionDef(XMLDBGetChildCollections.signature, XMLDBGetChildCollections.class),
		new FunctionDef(XMLDBGetResourceCollections.signature, XMLDBGetResourceCollections.class),
		new FunctionDef(XMLDBSetCollectionPermissions.signature, XMLDBSetCollectionPermissions.class),
		new FunctionDef(XMLDBSetResourcePermissions.signature, XMLDBSetResourcePermissions.class),
       		new FunctionDef(XMLDBCreateUser.signature, XMLDBCreateUser.class),
        	new FunctionDef(XMLDBDeleteUser.signature, XMLDBDeleteUser.class),
	        new FunctionDef(XMLDBChmodCollection.signature, XMLDBChmodCollection.class),
	        new FunctionDef(XMLDBChmodResource.signature, XMLDBChmodResource.class),
	        new FunctionDef(XMLDBCollectionExists.signature, XMLDBCollectionExists.class),
	};
	
	public XMLDBModule() {
		super(functions);
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

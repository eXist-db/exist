/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
package org.exist.xmldb.test.concurrent;

import org.exist.xmldb.XPathQueryServiceImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class XQueryAction extends Action {
	
	private String xquery;
	
	/**
	 * @param name
	 * @param uri
	 * @param testCollection
	 */
	public XQueryAction(String collectionPath, String resourceName, String xquery) {
		super(collectionPath, resourceName);
		this.xquery = xquery;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.Action#execute()
	 */
	public boolean execute() throws Exception {
	    Thread.currentThread().setName("XQuery Thread");
	    
		Collection col = DatabaseManager.getCollection(collectionPath);
		System.out.println(Thread.currentThread().getName() + ": executing query: " + xquery);
		XPathQueryServiceImpl service = (XPathQueryServiceImpl)
			col.getService("XPathQueryService", "1.0");
		
		service.beginProtected();
		ResourceSet result = service.query(xquery);
		System.out.println(Thread.currentThread().getName() + ": found " + result.getSize());
		
		DefaultHandler handler = new DefaultHandler();
		for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
			XMLResource next = (XMLResource) i.nextResource();
			next.getContentAsSAX(handler);
		}
		service.endProtected();
		
		return false;
	}
}

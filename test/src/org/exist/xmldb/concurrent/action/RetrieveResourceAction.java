/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xmldb.concurrent.action;

import org.xml.sax.helpers.DefaultHandler;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.XMLResource;


public class RetrieveResourceAction extends Action {
	
	/**
	 * 
	 */
	public RetrieveResourceAction(String collectionPath, String resourceName) {
		super(collectionPath, resourceName);
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentXUpdateTest.Action#execute()
	 */
	public boolean execute() throws Exception {
		Collection col = DatabaseManager.getCollection(collectionPath);
		XMLResource res = (XMLResource)col.getResource(resourceName);
		
//		System.out.println(res.getContent());
		DefaultHandler handler = new DefaultHandler();
		res.getContentAsSAX(handler);
		
		System.out.println(Thread.currentThread()
				+ " - Retrieved resource: " + resourceName);
		return false;
	}
}
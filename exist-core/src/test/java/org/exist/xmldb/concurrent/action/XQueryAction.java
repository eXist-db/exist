/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb.concurrent.action;

import org.exist.xmldb.EXistXPathQueryService;
import org.xml.sax.helpers.DefaultHandler;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class XQueryAction extends Action {
	
	private final String xquery;
	private long runningTime = 0;
	private int called = 0;

	public XQueryAction(final String collectionPath, final String resourceName, final String xquery) {
		super(collectionPath, resourceName);
		this.xquery = xquery;
	}

	@Override
	public boolean execute() throws XMLDBException {
	    final long start = System.currentTimeMillis();
	    
		final Collection col = DatabaseManager.getCollection(collectionPath);
		
		final EXistXPathQueryService service = col.getService(EXistXPathQueryService.class);
		
//		service.beginProtected();
		final ResourceSet result = service.query(xquery);
		
		final DefaultHandler handler = new DefaultHandler();
		for (int i = 0; i < result.getSize(); i++) {
			final XMLResource next = (XMLResource) result.getResource(i);
			next.getContentAsSAX(handler);
		}
//		service.endProtected();
		
		runningTime += (System.currentTimeMillis() - start);
		called++;

		return true;
	}

	public String getQuery() {
	    return xquery;
	}

	public long avgExecTime() {
	    return called == 0 ? 0 : runningTime / called;
	}
}

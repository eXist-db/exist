/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb.concurrent.action;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

import static org.junit.Assert.assertEquals;

/**
 * @author wolf
 */
public class ValueAppendAction extends Action {

    private static final String REMOVE =
        "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">"
        + "<xu:remove select=\"//item[last()]\">"
        + "</xu:remove>"
        + "</xu:modifications>";
    
    public ValueAppendAction(final String collectionPath, final String resourceName) {
		super(collectionPath, resourceName);
	}
    
    @Override
    public boolean execute() throws XMLDBException {
        final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
		final XUpdateQueryService service = (XUpdateQueryService)
			col.getService("XUpdateQueryService", "1.0");
		final XPathQueryService query = (XPathQueryService)
    		col.getService("XPathQueryService", "1.0");
		append(service);
		query(query);
        remove(service);
        return true;
    }

    private void remove(final XUpdateQueryService service) throws XMLDBException {
		for(int i = 0; i < 10; i++) {
			service.update(REMOVE);
		}
	}
    
    private void append(final XUpdateQueryService service) throws XMLDBException {
		final String updateOpen =
			"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
			"<xu:append select=\"/items\" child=\"1\">";
		final String updateClose =
			"</xu:append>" +
			"</xu:modifications>";
		for (int i = 0; i < 10; i++) {
			final String update = updateOpen +
				"<item id=\"" + i + "\"><name>abcdefg</name>" +
				"<value>" + (44.53 + i) + "</value></item>"
				+ updateClose;
			service.update(update);
		}
	}
    
    private void query(final XPathQueryService service) throws XMLDBException {
        ResourceSet result = service.queryResource(resourceName, "/items/item[value = 44.53]");
        assertEquals(1, result.getSize());
        result = service.queryResource(resourceName, "/items/item[@id=1]/name[.='abcdefg']/text()");
        assertEquals(1, result.getSize());
        assertEquals("abcdefg", result.getResource(0).getContent().toString());
    }
}

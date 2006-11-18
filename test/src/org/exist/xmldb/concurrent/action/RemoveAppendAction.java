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

import org.exist.xmldb.concurrent.XMLGenerator;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * Removes the 10 last elements from the resource and inserts 10 
 * new elements at the top.
 * 
 * @author wolf
 */
public class RemoveAppendAction extends Action {
	
	private static final String REMOVE =
        "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">"
        + "<xu:remove select=\"//ELEMENT[last()]\">"
        + "</xu:remove>"
        + "</xu:modifications>";
	
	protected XMLGenerator xmlGenerator;
	
	public RemoveAppendAction(String collectionPath, String resourceName, String[] wordList) {
		super(collectionPath, resourceName);
		xmlGenerator = new XMLGenerator(1, 5, 1, wordList, false);
	}
			
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentXUpdateTest.Action#execute()
	 */
	public boolean execute() throws Exception {
		Collection col = DatabaseManager.getCollection(collectionPath, "admin", null);
		XUpdateQueryService service = (XUpdateQueryService)
			col.getService("XUpdateQueryService", "1.0");
		append(service);
        remove(service);
        return false;
	}
	
	private void remove(XUpdateQueryService service) throws XMLDBException {
		System.out.println(Thread.currentThread().getName() + ": removing elements ...");
		for(int i = 0; i < 10; i++)
			service.update(REMOVE);
	}
	
	private void append(XUpdateQueryService service) throws Exception {
		final String updateOpen =
			"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
			"<xu:append select=\"/ROOT-ELEMENT\" child=\"1\">";
		final String updateClose =
			"</xu:append>" +
			"</xu:modifications>";
		System.out.println(Thread.currentThread().getName() + ": inserting elements ...");
		for (int i = 0; i < 10; i++) {
			String update = updateOpen + xmlGenerator.generateElement() + updateClose;
			service.update(update);
		}
	}
}
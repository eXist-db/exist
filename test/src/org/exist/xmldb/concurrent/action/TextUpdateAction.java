/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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

import java.util.Random;

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
public class TextUpdateAction extends Action {

	private static final String UPDATE_START =
		"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
		"<xu:update select=\"/article/section\">" +
		"<para>";

	private static final String UPDATE_END =
		"</para>" +
		"</xu:update>" +
		"</xu:modifications>";

	private static final String APPEND =
		"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
		"<xu:append select=\"/article\">" +
		"<section>" +
		"<title>Hello</title>" +
		"<para>Hello World!</para>" +
		"</section>" +
		"</xu:append>" +
		"</xu:modifications>";
	
	private final static String REMOVE =
		"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
		"<xu:remove select=\"/article/section\"/>" +
		"</xu:modifications>";
	
	private final Random rand;

	public TextUpdateAction(final String collectionPath, final String resourceName) {
		super(collectionPath, resourceName);
		rand = new Random();
	}

	@Override
	public boolean execute() throws XMLDBException {
		final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
		final XUpdateQueryService service = (XUpdateQueryService)
			col.getService("XUpdateQueryService", "1.0");
		
		// append a new section
		long mods = service.update(APPEND);
		assertEquals(1, mods);
		
		// update paragraph content
		String updateText = Integer.toString(rand.nextInt()) + " &amp; " + Integer.toString(rand.nextInt());
		final String update = UPDATE_START + updateText + UPDATE_END;
		mods = service.update(update);
		
		assertEquals(1, mods);
		
		// query for section
		final XPathQueryService query = (XPathQueryService) col.getService("XPathQueryService", "1.0");
		ResourceSet result = query.query("/article/section/para/text()");
		assertEquals(1, result.getSize());
		updateText = result.getResource(0).getContent().toString();
		result = query.query("/article/section/para[. = '" + updateText + "']");
		assertEquals(1, result.getSize());
		result.getResource(0).getContent();
		
		mods = service.update(REMOVE);
		assertEquals(1, mods);
		return true;
	}
}

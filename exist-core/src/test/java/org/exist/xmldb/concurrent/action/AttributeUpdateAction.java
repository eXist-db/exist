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

import java.util.Random;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author wolf
 */
public class AttributeUpdateAction extends RemoveAppendAction {

	private static final String XUPDATE_START =
        "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">"
        + "<xu:update select=\"//ELEMENT/@attribute-1\">";
	
	private static final String XUPDATE_END =
		"</xu:update>" +
		"</xu:modifications>";
	
	private final Random rand = new Random();

	public AttributeUpdateAction(final String collectionPath, final String resourceName, final String[] wordList) {
		super(collectionPath, resourceName, wordList);
	}

	@Override
	public boolean execute() throws XMLDBException {
		final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
		final XUpdateQueryService service = (XUpdateQueryService) col.getService("XUpdateQueryService", "1.0");
		final int attrSize = rand.nextInt(5);
		for (int i = 0; i < 10; i++) {
			final String xupdate = XUPDATE_START + xmlGenerator.generateText(attrSize) + XUPDATE_END;
			long mods = service.update(xupdate);
		}
		return true;
	}
}

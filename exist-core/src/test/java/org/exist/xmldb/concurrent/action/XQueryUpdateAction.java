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

import org.exist.TestUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

public class XQueryUpdateAction extends Action {

	private static final String query =
		"util:exclusive-lock(collection('/db/C1'),\n" +
		"	let $maxId := max(for $i in //node/@id return xs:integer($i)) + 1\n" +
		"	let $update :=\n" +
		"		<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">\n" +
		"			<xu:append select=\"/root\">\n" +
		"				<node id=\"{$maxId}\">appended node</node>\n" +
		"			</xu:append>\n" +
		"		</xu:modifications>\n" +
		"	return\n" +
		"		xmldb:update('/db/C1', $update)" +
		")";
	
	public XQueryUpdateAction(final String collectionPath, final String resourceName) {
		super(collectionPath, resourceName);
	}

	@Override
	public boolean execute() throws XMLDBException {
		final Collection col = DatabaseManager.getCollection(collectionPath, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
		final XQueryService service = (XQueryService) col.getService("XQueryService", "1.0");
		
		service.query(query);
		return true;
	}
}

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
package org.exist.xmldb.concurrent.action;

import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * Replace an existing resource.
 * 
 * @author wolf
 */
public class ReplaceResourceAction extends Action {

	public static final String XML =
		"<config>" +
		"<user id=\"george\">" +
		"<phone>+49 69 888478</phone>" +
		"<email>george@email.com</email>" +
		"<customer-id>64534233</customer-id>" +
		"<bank-account>7466356</bank-account>" +
		"</user>" +
		"<user id=\"sam\">" +
		"<phone>+49 69 774345</phone>" +
		"<email>sam@email.com</email>" +
		"<customer-id>993834</customer-id>" +
		"<bank-account>364553</bank-account>" +
		"</user>" +
		"</config>";
	
	private final static String TEST_QUERY1 = "//user[@id = 'george']/phone[contains(., '69')]/text()";
	private final static String TEST_QUERY2 = "//user[@id = 'sam']/customer-id[. = '993834']";
	private final static String TEST_QUERY3 = "//user[email = 'sam@email.com']";
	
	private int count = 0;

	public ReplaceResourceAction(final String collectionPath, final String resourceName) {
		super(collectionPath, resourceName);
	}

	@Override
	public boolean execute() throws XMLDBException {
		final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
		final String xml =
			"<data now=\"" + System.currentTimeMillis() + "\" count=\"" +
			++count + "\">" + XML + "</data>";
			
		DBUtils.addXMLResource(col, resourceName, xml);
		
		ResourceSet result = DBUtils.queryResource(col, resourceName, TEST_QUERY1);
		assertEquals(1, result.getSize());
		assertEquals("+49 69 888478", result.getResource(0).getContent());
		
		result = DBUtils.queryResource(col, resourceName, TEST_QUERY2);
		assertEquals(1, result.getSize());
		
		result = DBUtils.queryResource(col, resourceName, TEST_QUERY3);
		assertEquals(1, result.getSize());
		return true;
	}
}

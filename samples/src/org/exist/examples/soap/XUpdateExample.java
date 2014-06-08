/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.examples.soap;

import org.exist.soap.Admin;
import org.exist.soap.AdminService;
import org.exist.soap.AdminServiceLocator;
import org.exist.soap.Query;
import org.exist.soap.QueryService;
import org.exist.soap.QueryServiceLocator;
import org.exist.xmldb.XmldbURI;
import org.exist.xupdate.XUpdateProcessor;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Execute xupdate via SOAP. First create /db/test collection in database.
 *
 * Execute: bin\run.bat org.exist.examples.soap.XUpdateExample <query file>
 */
public class XUpdateExample {

	private final static String document =
		"<?xml version=\"1.0\"?>" +
		"<notes>" +
		"<note id=\"1\">Complete documentation.</note>" +
		"</notes>";
	
	private final static String xupdate =
		"<?xml version=\"1.0\"?>" +
		"<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
		"<xu:insert-after select=\"//note[1]\">" +
		"<xu:element name=\"note\">" +
		"<xu:attribute name=\"id\">2</xu:attribute>" +
		"Complete change log." +
		"</xu:element>" +
		"</xu:insert-after>" +
		"</xu:modifications>";
		
    public static void main( String[] args ) throws Exception {
        AdminService adminService = new AdminServiceLocator();
        Admin admin = adminService.getAdmin();
        QueryService queryService = new QueryServiceLocator();
        Query query = queryService.getQuery();
        
		String session = admin.connect("guest", "guest");
		admin.store(session, document.getBytes(UTF_8), "UTF-8", XmldbURI.ROOT_COLLECTION + "/test/notes.xml", true);
		admin.xupdateResource(session, XmldbURI.ROOT_COLLECTION + "/test/notes.xml", xupdate);
		
		String data = query.getResource(session, 
				XmldbURI.ROOT_COLLECTION + "/test/notes.xml",
			true, true);
		System.out.println(data);
		admin.disconnect(session);
    }
}


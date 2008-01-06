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

import org.exist.soap.Query;
import org.exist.soap.QueryService;
import org.exist.soap.QueryServiceLocator;
import org.exist.storage.DBBroker;

/**
 * Get document via SOAP. First insert shakespeare example documents.
 *
 * Execute: bin\run.bat org.exist.examples.soap.GetDocument
 */
public class GetDocument {

    public static void main( String[] args ) throws Exception {
        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();
		String session = query.connect("guest", "guest");
        
		byte[] data = query.getResourceData(session, 
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml",
			true, false, false);
		System.out.println(new String(data, "UTF-8"));
		query.disconnect(session);
    }
}


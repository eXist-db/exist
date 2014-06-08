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

import org.exist.soap.*;
import java.io.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class QueryExample {

    /**
     * Execute xquery via SOAP
     *
     * Execute: bin\run.bat org.exist.examples.soap.QueryExample <query file>
     */
    protected static String readFile(String file) throws IOException {
    	BufferedReader f = new BufferedReader(new FileReader(file));
    	String line;
    	StringBuffer xml = new StringBuffer();
    	while((line = f.readLine()) != null) {
    		xml.append(line);
            xml.append('\n');
        }
    	f.close();
    	return xml.toString();
    }

    public static void main( String[] args ) throws Exception {
        if(args.length != 1) {
            System.out.println("Usage: org.exist.examples.soap.QueryExample xquery-file");
            System.exit(0);
        }
        
    	String xquery = readFile(args[0]);
    	
        // we send the data in base64 encoding to avoid
        // difficulties with XML special chars in the query.
        byte[] queryData = xquery.getBytes(UTF_8);

        QueryService service = new QueryServiceLocator();
        Query query = service.getQuery();

		// connect to the database
		String sessionId = query.connect("guest", "guest");
		
		// execute the query
        QueryResponse resp =
        	query.xquery( sessionId, queryData );
        System.out.println( "found: " + resp.getHits() );
		if(resp.getHits() == 0)	// nothing found
			return;
        
        // retrieve first 10 results, indenting is on, xinclude is off, matches in elements 
        // are highlighted (tagged)
		byte[][] hits = query.retrieveData( sessionId, 1, 10,
			true, false, "elements").getElements();
		for(int i = 0; i < hits.length; i++) {
			System.out.println(new String(hits[i], "UTF-8"));
		}
		
		// close the connection (release session on the server)
		query.disconnect(sessionId);
    }
}


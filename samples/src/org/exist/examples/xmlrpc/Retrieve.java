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
package org.exist.examples.xmlrpc;

import java.util.Hashtable;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;

/**
 *  Retrieve a document from the database using XMLRPC.
 *
 * Execute bin\run.bat org.exist.examples.xmlrpc.Retrieve <remotedoc>
 *
 *  @author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *  @created    August 1, 2002
 */
public class Retrieve {

    protected final static String uri = "http://localhost:8080/exist/xmlrpc";

    protected static void usage() {
        System.out.println( "usage: org.exist.examples.xmlrpc.Retrieve path-to-document" );
        System.exit( 0 );
    }

    public static void main( String args[] ) throws Exception {
        if ( args.length < 1 ) {
            usage();
        }
        XmlRpc.setEncoding("UTF-8");
        XmlRpcClient xmlrpc = new XmlRpcClient( uri );
        Hashtable options = new Hashtable();
        options.put("indent", "yes");
        options.put("encoding", "UTF-8");
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");
        
        Vector params = new Vector();
        params.addElement( args[0] ); 
        params.addElement( options );
        String xml = (String)
            xmlrpc.execute( "getDocumentAsString", params );
        System.out.println( xml );
    }
}


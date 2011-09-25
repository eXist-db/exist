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

import java.util.Vector;
import java.util.HashMap;
import java.net.URL;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.util.SSLHelper;

/**
 *  Retrieve a document from the database using XMLRPC.
 *
 * Execute bin\run.bat org.exist.examples.xmlrpc.Retrieve <remotedoc>
 *
 *  @author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *  created    August 1, 2002
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
        
        // Initialize HTTPS connection to accept selfsigned certificates
        // and the Hostname is not validated 
        SSLHelper.initialize();
        
        
        XmlRpcClient client = new XmlRpcClient();
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(uri));
        config.setBasicUserName("guest");
        config.setBasicPassword("guest");
        client.setConfig(config);

        HashMap<String, String> options = new HashMap<String, String>();
        options.put("indent", "yes");
        options.put("encoding", "UTF-8");
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");
        
        Vector<Object> params = new Vector<Object>();
        params.addElement( args[0] ); 
        params.addElement( options );
        String xml = (String)
            client.execute( "getDocumentAsString", params );
        System.out.println( xml );
    }
}


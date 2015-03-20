/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id: BaseConverterTest.java 10599 2009-11-26 05:23:12Z shabanovd $
 */
package org.exist.xquery.functions.util;

import org.junit.After;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;


/**
 * DOCUMENT ME!
 *
 * @author  Andrzej Taramina (andrzej@chaeron.com)
 */
public class Base64FunctionsTest
{
    private XPathQueryService service;
    private Collection        root     = null;
    private Database          database = null;

    public Base64FunctionsTest()
    {
    }

    @Before public void setUp() throws Exception
    {
        // initialize driver
        Class<?> cl = Class.forName( "org.exist.xmldb.DatabaseImpl" );
        database = (Database)cl.newInstance();
        database.setProperty( "create-database", "true" );
        DatabaseManager.registerDatabase( database );
        root    = DatabaseManager.getCollection( XmldbURI.LOCAL_DB, "admin", "" );
        service = (XPathQueryService)root.getService( "XQueryService", "1.0" );
    }


    @After public void tearDown() throws Exception
    {
        DatabaseManager.deregisterDatabase( database );
        DatabaseInstanceManager dim = (DatabaseInstanceManager)root.getService( "DatabaseInstanceManager", "1.0" );
        dim.shutdown();

        // clear instance variables
        service = null;
        root    = null;
    }


    @Test public void testBase64Encode() throws XPathException
    {
        ResourceSet result = null;
        String      r      = "";
		String query;

        try {
			query = "util:base64-encode( 'This is a test!' )";
            result = service.query( query );
            r      = (String)result.getResource( 0 ).getContent();
            assertEquals( "VGhpcyBpcyBhIHRlc3Qh", r );
        }
        catch( XMLDBException e ) {
            fail( e.getMessage() );
        }

    }
	
	@Test public void testBase64Decode() throws XPathException
    {
        ResourceSet result = null;
        String      r      = "";
		String query;

        try {		
			query = "util:base64-decode( 'VGhpcyBpcyBhIHRlc3Qh' )";
            result = service.query( query );
            r      = (String)result.getResource( 0 ).getContent();
            assertEquals( "This is a test!", r );		
        }
        catch( XMLDBException e ) {
            fail( e.getMessage() );
        }

    }
		
	@Test public void testBase64EncodeDecode() throws XPathException
    {
        ResourceSet result = null;
        String      r      = "";
		String query;

        try {
			query = "util:base64-decode( util:base64-encode( 'This is a test!' ) )";
            result = service.query( query );
            r      = (String)result.getResource( 0 ).getContent();
            assertEquals( "This is a test!", r );
        }
        catch( XMLDBException e ) {
            fail( e.getMessage() );
        }

    }


}

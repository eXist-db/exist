/*
 * Created on 04.07.2005 - $Id$
 */
package org.exist.xquery;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.assertEquals;

/** Tests for various XQuery (XML Schema) simple types conversions.
 * @author jmvanel
 */
public class ConversionsTest {

	private XPathQueryService service;
	private Collection root = null;
	private Database database = null;
	

/** test conversion from QName to string */
	@Test
	public void qname2string() throws XMLDBException {
		ResourceSet result 	= null;
		String		r		= "";
		String		query	= null;

        query = "declare namespace foo = 'http://foo'; \n" +
                "let $a := ( xs:QName('foo:bar'), xs:QName('foo:john'), xs:QName('foo:doe') )\n" +
                    "for $b in $a \n" +
                        "return \n" +
                            "<blah>{string($b)}</blah>" ;
        result 	= service.query( query );
        /* which returns :
            <blah>foo:bar</blah>
            <blah>foo:john</blah>
            <blah>foo:doe</blah>"
        */
        r = (String) result.getResource(0).getContent();
        assertEquals( "<blah>foo:bar</blah>", r );
        assertEquals( "XQuery: " + query, 3, result.getSize() );
	}

	
	@Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
	}

	@After
	public void tearDown() throws XMLDBException {
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
            (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
	}
}

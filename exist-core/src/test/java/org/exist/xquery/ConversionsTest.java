/*
 * Created on 04.07.2005 - $Id$
 */
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/** Tests for various XQuery (XML Schema) simple types conversions.
 * @author jmvanel
 */
public class ConversionsTest {

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	/** test conversion from QName to string */
	@Test
	public void qname2string() throws XMLDBException {
        final String query = "declare namespace foo = 'http://foo'; \n" +
                "let $a := ( xs:QName('foo:bar'), xs:QName('foo:john'), xs:QName('foo:doe') )\n" +
                    "for $b in $a \n" +
                        "return \n" +
                            "<blah>{string($b)}</blah>" ;
        final ResourceSet result = existEmbeddedServer.executeQuery( query );
        /* which returns :
            <blah>foo:bar</blah>
            <blah>foo:john</blah>
            <blah>foo:doe</blah>"
        */
        final String r = (String) result.getResource(0).getContent();
        assertEquals( "<blah>foo:bar</blah>", r );
        assertEquals( "XQuery: " + query, 3, result.getSize() );
	}
}

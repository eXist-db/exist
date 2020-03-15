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

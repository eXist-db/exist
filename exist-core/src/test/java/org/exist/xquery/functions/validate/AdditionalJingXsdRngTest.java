/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
package org.exist.xquery.functions.validate;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;
import static org.junit.Assert.*;

import org.exist.xquery.XPathException;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * Additional tests for the validation:jing() function with RNGs and XSDs
 *
 * @author jim.fuller@webcomposite.com
 * @author dizzzz@exist-db.org
 */
public class AdditionalJingXsdRngTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void testValidateXSDwithJing() throws XMLDBException {
        final String query = "let $v := <doc>\n" +
                "\t<title>Title</title>\n" +
                "\t<p>Some paragraph.</p>\n" +
                "      </doc>\n" +
                "let $schema := <xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "\t\t elementFormDefault=\"qualified\">\n" +
                "\t<xs:element name=\"doc\">\n" +
                "\t  <xs:complexType>\n" +
                "\t    <xs:sequence>\n" +
                "\t      <xs:element minOccurs=\"0\" ref=\"title\"/>\n" +
                "\t      <xs:element minOccurs=\"0\" maxOccurs=\"unbounded\" ref=\"p\"/>\n" +
                "\t    </xs:sequence>\n" +
                "\t  </xs:complexType>\n" +
                "\t</xs:element>\n" +
                "\t<xs:element name=\"title\" type=\"xs:string\"/>\n" +
                "\t<xs:element name=\"p\" type=\"xs:string\"/>\n" +
                "      </xs:schema>\n" +
                "return\n" +
                "\n" +
                "\tvalidation:jing($v,$schema)";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("true", r);
    }

    @Test
    public void testValidateXSDwithJing_invalid() throws XMLDBException {
        final String query = "let $v := <doc>\n" +
                "\t<title1>Title</title1>\n" +
                "\t<p>Some paragraph.</p>\n" +
                "      </doc>\n" +
                "let $schema := <xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "\t\t elementFormDefault=\"qualified\">\n" +
                "\t<xs:element name=\"doc\">\n" +
                "\t  <xs:complexType>\n" +
                "\t    <xs:sequence>\n" +
                "\t      <xs:element minOccurs=\"0\" ref=\"title\"/>\n" +
                "\t      <xs:element minOccurs=\"0\" maxOccurs=\"unbounded\" ref=\"p\"/>\n" +
                "\t    </xs:sequence>\n" +
                "\t  </xs:complexType>\n" +
                "\t</xs:element>\n" +
                "\t<xs:element name=\"title\" type=\"xs:string\"/>\n" +
                "\t<xs:element name=\"p\" type=\"xs:string\"/>\n" +
                "      </xs:schema>\n" +
                "return\n" +
                "\n" +
                "\tvalidation:jing($v,$schema)";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("false", r);
    }

    @Test
    public void testValidateRNGwithJing() throws XPathException, XMLDBException {
        final String query = "let $v := <doc>\n" +
                "\t<title>Title</title>\n" +
                "\t<p>Some paragraph.</p>\n" +
                "      </doc>\n" +
                "let $schema := <grammar xmlns=\"http://relaxng.org/ns/structure/1.0\">\n" +
                "  <start>\n" +
                "    <ref name=\"doc\"/>\n" +
                "  </start>\n" +
                "  <define name=\"doc\">\n" +
                "    <element name=\"doc\">\n" +
                "      <optional>\n" +
                "        <ref name=\"title\"/>\n" +
                "      </optional>\n" +
                "      <zeroOrMore>\n" +
                "        <ref name=\"p\"/>\n" +
                "      </zeroOrMore>\n" +
                "    </element>\n" +
                "  </define>\n" +
                "  <define name=\"title\">\n" +
                "    <element name=\"title\">\n" +
                "      <text/>\n" +
                "    </element>\n" +
                "  </define>\n" +
                "  <define name=\"p\">\n" +
                "    <element name=\"p\">\n" +
                "      <text/>\n" +
                "    </element>\n" +
                "  </define>\n" +
                "</grammar>\n" +
                "return\n" +
                "\n" +
                "\tvalidation:jing($v,$schema)";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("true", r);
    }

    @Test
    public void testValidateRNGwithJing_invalid() throws XMLDBException {
        final String query = "let $v := <doc>\n" +
                "\t<title1>Title</title1>\n" +
                "\t<p>Some paragraph.</p>\n" +
                "      </doc>\n" +
                "let $schema := <grammar xmlns=\"http://relaxng.org/ns/structure/1.0\">\n" +
                "  <start>\n" +
                "    <ref name=\"doc\"/>\n" +
                "  </start>\n" +
                "  <define name=\"doc\">\n" +
                "    <element name=\"doc\">\n" +
                "      <optional>\n" +
                "        <ref name=\"title\"/>\n" +
                "      </optional>\n" +
                "      <zeroOrMore>\n" +
                "        <ref name=\"p\"/>\n" +
                "      </zeroOrMore>\n" +
                "    </element>\n" +
                "  </define>\n" +
                "  <define name=\"title\">\n" +
                "    <element name=\"title\">\n" +
                "      <text/>\n" +
                "    </element>\n" +
                "  </define>\n" +
                "  <define name=\"p\">\n" +
                "    <element name=\"p\">\n" +
                "      <text/>\n" +
                "    </element>\n" +
                "  </define>\n" +
                "</grammar>\n" +
                "return\n" +
                "\n" +
                "\tvalidation:jing($v,$schema)";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("false", r);
    }

    @Test
    @Ignore("Looks good, but memory issue")
    public void repeatTests() throws XMLDBException, XPathException {
        for (int i = 0; i < 1000; i++) {
            testValidateRNGwithJing();
            testValidateRNGwithJing_invalid();
            testValidateXSDwithJing();
            testValidateXSDwithJing_invalid();
        }
    }
}

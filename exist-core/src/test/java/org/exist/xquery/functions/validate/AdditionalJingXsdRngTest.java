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
package org.exist.xquery.functions.validate;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.XPathException;
import org.junit.*;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.*;

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
        final String query = """
                let $v := <doc>
                	<title>Title</title>
                	<p>Some paragraph.</p>
                      </doc>
                let $schema := <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                		 elementFormDefault="qualified">
                	<xs:element name="doc">
                	  <xs:complexType>
                	    <xs:sequence>
                	      <xs:element minOccurs="0" ref="title"/>
                	      <xs:element minOccurs="0" maxOccurs="unbounded" ref="p"/>
                	    </xs:sequence>
                	  </xs:complexType>
                	</xs:element>
                	<xs:element name="title" type="xs:string"/>
                	<xs:element name="p" type="xs:string"/>
                      </xs:schema>
                return
                
                	validation:jing($v,$schema)""";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("true", r);
    }

    @Test
    public void testValidateXSDwithJing_invalid() throws XMLDBException {
        final String query = """
                let $v := <doc>
                	<title1>Title</title1>
                	<p>Some paragraph.</p>
                      </doc>
                let $schema := <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                		 elementFormDefault="qualified">
                	<xs:element name="doc">
                	  <xs:complexType>
                	    <xs:sequence>
                	      <xs:element minOccurs="0" ref="title"/>
                	      <xs:element minOccurs="0" maxOccurs="unbounded" ref="p"/>
                	    </xs:sequence>
                	  </xs:complexType>
                	</xs:element>
                	<xs:element name="title" type="xs:string"/>
                	<xs:element name="p" type="xs:string"/>
                      </xs:schema>
                return
                
                	validation:jing($v,$schema)""";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("false", r);
    }

    @Test
    public void testValidateRNGwithJing() throws XPathException, XMLDBException {
        final String query = """
                let $v := <doc>
                	<title>Title</title>
                	<p>Some paragraph.</p>
                      </doc>
                let $schema := <grammar xmlns="http://relaxng.org/ns/structure/1.0">
                  <start>
                    <ref name="doc"/>
                  </start>
                  <define name="doc">
                    <element name="doc">
                      <optional>
                        <ref name="title"/>
                      </optional>
                      <zeroOrMore>
                        <ref name="p"/>
                      </zeroOrMore>
                    </element>
                  </define>
                  <define name="title">
                    <element name="title">
                      <text/>
                    </element>
                  </define>
                  <define name="p">
                    <element name="p">
                      <text/>
                    </element>
                  </define>
                </grammar>
                return
                
                	validation:jing($v,$schema)""";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("true", r);
    }

    @Test
    public void testValidateRNGwithJing_invalid() throws XMLDBException {
        final String query = """
                let $v := <doc>
                	<title1>Title</title1>
                	<p>Some paragraph.</p>
                      </doc>
                let $schema := <grammar xmlns="http://relaxng.org/ns/structure/1.0">
                  <start>
                    <ref name="doc"/>
                  </start>
                  <define name="doc">
                    <element name="doc">
                      <optional>
                        <ref name="title"/>
                      </optional>
                      <zeroOrMore>
                        <ref name="p"/>
                      </zeroOrMore>
                    </element>
                  </define>
                  <define name="title">
                    <element name="title">
                      <text/>
                    </element>
                  </define>
                  <define name="p">
                    <element name="p">
                      <text/>
                    </element>
                  </define>
                </grammar>
                return
                
                	validation:jing($v,$schema)""";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("false", r);
    }

    @Test
    public void repeatTests() throws XMLDBException, XPathException {
        for (int i = 0; i < 1000; i++) {
            testValidateRNGwithJing();
            testValidateRNGwithJing_invalid();
            testValidateXSDwithJing();
            testValidateXSDwithJing_invalid();
        }
    }
}

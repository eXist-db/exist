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
package org.exist.util.serializer;

import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.StringWriter;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The namespace declarations that {@link SAXSerializer#startElement} writes, for both of its overloads: what is
 * declared, in which order, and when a default namespace is cancelled. The expected output is what the serializer
 * wrote before {@code startElement} was split into steps.
 */
public class SAXSerializerNamespacesTest {

    private static final String XHTML = "http://www.w3.org/1999/xhtml";

    private static SAXSerializer serializer(final StringWriter out, final boolean enforceXhtml) {
        final Properties properties = new Properties();
        properties.setProperty(EXistOutputKeys.ENFORCE_XHTML, enforceXhtml ? "yes" : "no");
        return new SAXSerializer(out, properties);
    }

    private static AttributesImpl attributes(final String... uriLocalQnameValue) {
        final AttributesImpl attribs = new AttributesImpl();
        for (int i = 0; i < uriLocalQnameValue.length; i += 4) {
            attribs.addAttribute(uriLocalQnameValue[i], uriLocalQnameValue[i + 1], uriLocalQnameValue[i + 2], "CDATA", uriLocalQnameValue[i + 3]);
        }
        return attribs;
    }

    /** An element <a> that holds the element under test, written with the SAX overload. */
    private static String sax(final boolean enforceXhtml, final String namespaceURI, final String localName, final String qname,
            final AttributesImpl attribs, final String... prefixMappings) throws SAXException {
        final StringWriter out = new StringWriter();
        final SAXSerializer serializer = serializer(out, enforceXhtml);
        serializer.startElement("", "a", "a", new AttributesImpl());
        for (int i = 0; i < prefixMappings.length; i += 2) {
            serializer.startPrefixMapping(prefixMappings[i], prefixMappings[i + 1]);
        }
        serializer.startElement(namespaceURI, localName, qname, attribs);
        serializer.endElement(namespaceURI, localName, qname);
        serializer.endElement("", "a", "a");
        return out.toString();
    }

    /** The same with the overload that takes a QName. */
    private static String qname(final boolean enforceXhtml, final QName name, final AttrList attribs) throws SAXException {
        final StringWriter out = new StringWriter();
        final SAXSerializer serializer = serializer(out, enforceXhtml);
        final QName parent = new QName("a", "", "");
        serializer.startElement(parent, new AttrList());
        serializer.startElement(name, attribs);
        serializer.endElement(name);
        serializer.endElement(parent);
        return out.toString();
    }

    @Test
    void anElementWithAPrefixDeclaresItsNamespace() throws SAXException {
        assertEquals("<a><p:a xmlns:p=\"urn:x\"/></a>", sax(false, "urn:x", "a", "p:a", null));
    }

    @Test
    void aDefaultNamespaceDeclarationOfTheAttributesIsWrittenOnce() throws SAXException {
        assertEquals("<a><a xmlns=\"urn:d\" id=\"1\"/></a>", sax(false, "urn:d", "a", "a", attributes("", "xmlns", "xmlns", "urn:d", "", "id", "id", "1")));
    }

    @Test
    void aPrefixDeclarationAndAPrefixedAttributeAreDeclared() throws SAXException {
        assertEquals("<a><a xmlns=\"urn:x\" xmlns:p=\"urn:p\" p:c=\"v\"/></a>", sax(false, "urn:x", "a", "a", attributes("", "p", "xmlns:p", "urn:p", "urn:p", "c", "p:c", "v")));
    }

    @Test
    void theXhtmlNamespaceIsEnforcedOnElementsWithoutNamespace() throws SAXException {
        assertEquals("<a xmlns=\"http://www.w3.org/1999/xhtml\"><a/></a>", sax(true, null, "a", "a", null));
    }

    @Test
    void aPrefixMappingIsWrittenBeforeTheOtherDeclarations() throws SAXException {
        assertEquals("<a><a xmlns:p=\"urn:p\" id=\"1\"/></a>", sax(false, "", "a", "a", attributes("", "id", "id", "1"), "p", "urn:p"));
    }

    @Test
    void anElementOfAQNameDeclaresItsPrefixAndTheOneOfItsAttribute() throws SAXException {
        final AttrList attribs = new AttrList();
        attribs.addAttribute(new QName("c", "urn:z", "z"), "v");
        assertEquals("<a><p:a xmlns:p=\"urn:x\" xmlns:z=\"urn:z\" z:c=\"v\"/></a>", qname(false, new QName("a", "urn:x", "p"), attribs));
    }

    /** The default namespace is cancelled by the prefix of the last attribute that has one, not by the element's own. */
    @Test
    void theLastPrefixedAttributeDecidesWhetherTheDefaultNamespaceIsCancelled() throws SAXException {
        final AttrList attribs = new AttrList();
        attribs.addAttribute(new QName("xmlns", "", ""), XHTML);
        attribs.addAttribute(new QName("c", "urn:z", "z"), "v");
        assertEquals("<a><a xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:z=\"urn:z\" z:c=\"v\"/></a>", qname(false, new QName("a", "", ""), attribs));
    }

    @Test
    void theXhtmlNamespaceIsEnforcedOnAQNameWithoutNamespace() throws SAXException {
        assertEquals("<a xmlns=\"http://www.w3.org/1999/xhtml\"><a/></a>", qname(true, new QName("a", "", ""), null));
    }

    @Test
    void aDeclarationOfThePrefixXmlnsIsNotWrittenAsANamespace() throws SAXException {
        final AttrList attribs = new AttrList();
        attribs.addAttribute(new QName("p", "urn:p", "xmlns"), "urn:p");
        assertEquals("<a><a xmlns:p=\"urn:p\"/></a>", qname(false, new QName("a", "", ""), attribs));
    }
}

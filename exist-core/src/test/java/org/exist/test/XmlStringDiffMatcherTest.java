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
package org.exist.test;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xmlunit.builder.Input;
import org.xmlunit.matchers.HasXPathMatcher;
import org.xmlunit.util.Convert;

import java.util.Map;

import static org.exist.test.XmlStringDiffMatcher.hasIdenticalXml;
import static org.exist.test.XmlStringDiffMatcher.hasSimilarXml;
import static org.exist.test.XmlStringDiffMatcher.hasSimilarXmlIgnoringWhitespace;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;

/**
 * The XMLUnit 2 idioms that replace XMLUnit 1's {@code assertXMLEqual}, {@code assertXpathEvaluatesTo}
 * and {@code assertXpathExists} in the tests: {@link XmlStringDiffMatcher} and the XPath matcher of
 * {@code xmlunit-matchers}.
 * <p>
 * Every idiom is shown to pass and to fail: a matcher that cannot fail would make each test that uses
 * it pass, whatever the XML is.
 */
class XmlStringDiffMatcherTest {

    @Test
    void similarIgnoresTheOrderOfAttributes() {
        assertThat("<a x='1' y='2'/>", hasSimilarXml("<a y='2' x='1'/>"));
        assertThat("<a x='1' y='2'/>", hasIdenticalXml("<a y='2' x='1'/>"));
    }

    @Test
    void similarAcceptsAnotherNamespacePrefixButIdenticalDoesNot() {
        final String control = "<p:a xmlns:p='urn:x'/>";
        final String test = "<q:a xmlns:q='urn:x'/>";

        assertThat(test, hasSimilarXml(control));
        assertThrows(AssertionError.class, () -> assertThat(test, hasIdenticalXml(control)));
    }

    @Test
    void bothRejectAnotherAttributeValue() {
        assertThrows(AssertionError.class, () -> assertThat("<a x='2'/>", hasSimilarXml("<a x='1'/>")));
        assertThrows(AssertionError.class, () -> assertThat("<a x='2'/>", hasIdenticalXml("<a x='1'/>")));
    }

    @Test
    void bothRejectAnotherText() {
        assertThrows(AssertionError.class, () -> assertThat("<a>two</a>", hasSimilarXml("<a>one</a>")));
        assertThrows(AssertionError.class, () -> assertThat("<a>two</a>", hasIdenticalXml("<a>one</a>")));
    }

    @Test
    void bothRejectAnotherElementName() {
        assertThrows(AssertionError.class, () -> assertThat("<b/>", hasSimilarXml("<a/>")));
        assertThrows(AssertionError.class, () -> assertThat("<b/>", hasIdenticalXml("<a/>")));
    }

    @Test
    void whitespaceBetweenElementsMattersUnlessItIsIgnored() {
        final String control = "<a><b/></a>";
        final String test = "<a>\n  <b/>\n</a>";

        assertThrows(AssertionError.class, () -> assertThat(test, hasSimilarXml(control)));
        assertThat(test, hasSimilarXmlIgnoringWhitespace(control));
    }

    @Test
    void ignoringWhitespaceStillRejectsAnotherText() {
        assertThrows(AssertionError.class, () -> assertThat("<a>\n two\n</a>", hasSimilarXmlIgnoringWhitespace("<a>one</a>")));
    }

    @Test
    void aFailureNamesTheDifference() {
        final AssertionError error = assertThrows(AssertionError.class,
                () -> assertThat("<a x='2'/>", hasSimilarXml("<a x='1'/>")));

        assertThat(error.getMessage(), containsString("differences:"));
        assertThat(error.getMessage(), containsString("attribute value"));
    }

    @Test
    void xpathComparesTheStringValue() {
        final String xml = "<a><b>1</b><b>2</b></a>";

        assertThat(xml, hasXPath("count(/a/b)", equalTo("2")));
        assertThat(xml, hasXPath("/a/b[1]/text()", equalTo("1")));
        assertThrows(AssertionError.class, () -> assertThat(xml, hasXPath("count(/a/b)", equalTo("3"))));
    }

    @Test
    void xpathExistsFailsWhenThereIsNoMatch() {
        final String xml = "<a><b/></a>";

        assertThat(xml, HasXPathMatcher.hasXPath("//b"));
        assertThrows(AssertionError.class, () -> assertThat(xml, HasXPathMatcher.hasXPath("//c")));
    }

    @Test
    void xpathUsesTheNamespaceContextItIsGiven() {
        final String xml = "<m xmlns:e='urn:exist'><e:match/><e:match/></m>";

        assertThat(xml, hasXPath("count(//e:match)", equalTo("2")).withNamespaceContext(Map.of("e", "urn:exist")));
        // the same prefix bound to another namespace matches nothing
        assertThrows(AssertionError.class, () ->
                assertThat(xml, hasXPath("count(//e:match)", equalTo("2")).withNamespaceContext(Map.of("e", "urn:other"))));
    }

    @Test
    void xpathAcceptsADocument() {
        final Document document = Convert.toDocument(Input.fromString("<a><b>1</b></a>").build());

        assertThat(document, hasXPath("/a/b/text()", equalTo("1")));
        assertThrows(AssertionError.class, () -> assertThat(document, hasXPath("/a/b/text()", equalTo("2"))));
    }
}

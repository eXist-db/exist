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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * The {@code exist:preserve-cdata} serialization parameter — see
 * <a href="https://github.com/eXist-db/exist/issues/2081">issue #2081</a>.
 *
 * <p>XDM has no CDATA node kind, so {@code fn:serialize} escapes the content of a CDATA section
 * unless the containing element is named in {@code cdata-section-elements}. That is correct per
 * the W3C serialization spec, but it leaves no way to get a stored document back in the form it
 * was stored in: eXist's DOM does keep CDATA nodes, and its REST serializer emits them, but that
 * was unreachable from {@code fn:serialize}. An editor round-tripping a document needs exactly
 * that, and naming elements in {@code cdata-section-elements} is not a substitute — it imposes
 * CDATA on elements that never had it.</p>
 *
 * <p>The parameter is opt-in and defaults to false, so unqualified {@code fn:serialize} behaviour
 * is unchanged.</p>
 */
public class PreserveCdataSerializationTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String DOC = "/db/preserve-cdata-test.xml";

    /** Built by concatenation so this file need not nest a CDATA section inside one. */
    private static final String SOURCE =
            "'<doc><p>' || '<' || '![CDATA[ a > b ]]' || '>' || '</p></doc>'";

    private static final String PRESERVE =
            "QName('http://exist.sourceforge.net/NS/exist','preserve-cdata'): true()";

    private static String query(final String xquery) throws XMLDBException {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query("xquery version \"3.1\"; " + xquery);
        return rs.getSize() == 0 ? "" : String.valueOf(rs.getResource(0).getContent());
    }

    @BeforeClass
    public static void storeTestDocument() throws XMLDBException {
        query("let $s := " + SOURCE
                + " return xmldb:store('/db', 'preserve-cdata-test.xml', $s, 'application/xml')");
    }

    @AfterClass
    public static void removeTestDocument() throws XMLDBException {
        query("xmldb:remove('/db', 'preserve-cdata-test.xml')");
    }

    /** Without the parameter, the spec's escaping applies — this must not change. */
    @Test
    public void withoutTheParameterContentIsEscaped() throws XMLDBException {
        assertEquals("<doc><p> a &gt; b </p></doc>",
                query("serialize(doc('" + DOC + "'), map { 'method': 'xml' })"));
    }

    /** With it, a stored CDATA section comes back as a CDATA section. */
    @Test
    public void withTheParameterAStoredSectionIsPreserved() throws XMLDBException {
        assertEquals("<doc><p><![CDATA[ a > b ]]></p></doc>",
                query("serialize(doc('" + DOC + "'), map { 'method': 'xml', " + PRESERVE + " })"));
    }

    /** It works for an in-memory document too, not only a stored one. */
    @Test
    public void itAppliesToInMemoryDocumentsAsWell() throws XMLDBException {
        assertEquals("<doc><p><![CDATA[ a > b ]]></p></doc>",
                query("serialize(parse-xml(" + SOURCE + "), map { 'method': 'xml', " + PRESERVE + " })"));
    }

    /** Explicitly false is the same as omitting it. */
    @Test
    public void explicitFalseMatchesTheDefault() throws XMLDBException {
        assertEquals("<doc><p> a &gt; b </p></doc>",
                query("serialize(doc('" + DOC + "'), map { 'method': 'xml', "
                        + "QName('http://exist.sourceforge.net/NS/exist','preserve-cdata'): false() })"));
    }

    /** A document with no CDATA section is unaffected either way. */
    @Test
    public void documentsWithoutCdataAreUnaffected() throws XMLDBException {
        final String plain = "parse-xml('<doc><p>a &amp;gt; b</p></doc>')";
        assertEquals(query("serialize(" + plain + ", map { 'method': 'xml' })"),
                query("serialize(" + plain + ", map { 'method': 'xml', " + PRESERVE + " })"));
    }
}

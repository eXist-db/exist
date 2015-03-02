/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.indexing.ngram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MatchListenerTest {

    private static String XML = "<root>" + "   <para>some paragraph with <hi>mixed</hi> content.</para>"
        + "   <para>another paragraph with <note><hi>nested</hi> inner</note> elements.</para>"
        + "   <para>a third paragraph with <term>term</term>.</para>" + "   <para>double match double match</para>"
        + "   <para>abaaba</para>" + "   <para>aaa aaa aaa</para>" + "    <para>Where did all the *s go?</para>"
        + "   <para>aaacaaa</para>"
        + "<para>test]test test[test test?test</para>"
        + "<para>a simple paragraph</para>"
        + "   <para>ucjkewbuwdcoikjewkj</para><para>ucjkewboislksoikjewkj</para><para>ucjkewbsdcoikjewkj</para><para>ucjkewbaaasaaacoikjewkj</para>"
        + "</root>";

    private static String XML2 =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<p xmlns=\"http://www.tei-c.org/ns/1.0\" xml:id=\"pT01p0257c1501\">爾時會中。有一尊者。名曰龍護。手執寶拂。 \n" +
        "    <lb n=\"0257c16\" ed=\"T\"/>侍立佛側。時尊者龍護白佛言。世尊。我見 \n" +
        "    <lb n=\"0257c17\" ed=\"T\"/>諸邪外道尼乾子等。於佛世尊。先不起信。 \n" +
        "    <lb n=\"0257c18\" ed=\"T\"/>唯於邪道。競說勝能。是故我今建立表剎 \n" +
        "    <lb n=\"0257c19\" ed=\"T\"/>宣示於世。咸使聞知佛勝功德。於佛世尊。是 \n" +
        "    <lb n=\"0257c20\" ed=\"T\"/>大丈夫。最尊最上。無有等者。\n" +
        "</p>";
    
    private static String CONF1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
    	"		</fulltext>" +
    	"		<ngram qname=\"para\"/>" +
        "		<ngram qname=\"term\"/>" +
        "	</index>" +
    	"</collection>";

    private static String CONF2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
    	"		<fulltext default=\"none\">" +
    	"		</fulltext>" +
    	"		<ngram qname=\"note\"/>" +
        "	</index>" +
    	"</collection>";

    private static String CONF3 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
    	"		<fulltext default=\"none\">" +
    	"		</fulltext>" +
    	"		<ngram qname=\"tei:p\"/>" +
        "	</index>" +
    	"</collection>";

    private static String MATCH_START = "<exist:match xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
    private static String MATCH_END = "</exist:match>";

    private static BrokerPool pool;

    @Test
    public void nestedContent() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ngram:contains(., 'mixed')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                    MATCH_END + "</hi> content.</para>", result);

            seq = xquery.execute("//para[ngram:contains(., 'content')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>mixed</hi> " + MATCH_START + "content" +
                    MATCH_END + ".</para>", result);

            seq = xquery.execute("//para[ngram:contains(., 'nested')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" + MATCH_END +
                    "</hi> inner</note> elements.</para>", result);

            seq = xquery.execute("//para[ngram:contains(., 'content') and ngram:contains(., 'mixed')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" + MATCH_END +
                    "</hi> " + MATCH_START + "content" + MATCH_END + ".</para>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void matchInParent() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ngram:contains(., 'mixed')]/hi", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<hi>" + MATCH_START + "mixed" + MATCH_END + "</hi>", result);

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void matchInAncestor() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ngram:contains(., 'nested')]/note", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<note><hi>" + MATCH_START + "nested" + MATCH_END + "</hi> inner</note>", result);

            seq = xquery.execute("//para[ngram:contains(., 'nested')]//hi", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<hi>" + MATCH_START + "nested" + MATCH_END + "</hi>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void nestedIndex() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);

            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ngram:contains(term, 'term')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END + "</term>.</para>", result);

            seq = xquery.execute("//term[ngram:contains(., 'term')]/..", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END + "</term>.</para>", result);

            seq = xquery.execute("//term[ngram:contains(., 'term')]/ancestor::para", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END + "</term>.</para>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void mixedContentQueries() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ngram:contains(., 'mixed content')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                    MATCH_END + "</hi>" + MATCH_START + " content" + MATCH_END + ".</para>", result);

            seq = xquery.execute("//para[ngram:contains(., 'with mixed content')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph " + MATCH_START + "with " + MATCH_END + "<hi>" +
                    MATCH_START + "mixed" + MATCH_END + "</hi>" + MATCH_START + " content" + MATCH_END +
                ".</para>", result);

            seq = xquery.execute("//para[ngram:contains(., 'with nested')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph " + MATCH_START + "with " + MATCH_END +
                "<note><hi>" + MATCH_START + "nested" + MATCH_END + "</hi> inner</note> elements.</para>", result);

            seq = xquery.execute("//para[ngram:contains(., 'with nested inner elements')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph " + MATCH_START + "with " + MATCH_END +
                "<note><hi>" + MATCH_START + "nested" + MATCH_END + "</hi>" + MATCH_START + " inner" + MATCH_END +
                "</note>" + MATCH_START + " elements" + MATCH_END + ".</para>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void indexOnInnerElement() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF2, XML);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute("//para[ngram:contains(note, 'nested inner')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" + MATCH_END +
                "</hi>" + MATCH_START + " inner" + MATCH_END + "</note> elements.</para>", result);

            seq = xquery.execute("//note[ngram:contains(., 'nested inner')]/parent::para", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" + MATCH_END +
                "</hi>" + MATCH_START + " inner" + MATCH_END + "</note> elements.</para>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void doubleMatch() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            Sequence seq = xquery.execute("//para[ngram:contains(., 'double match')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "double match" + MATCH_END + " " +
                MATCH_START + "double match" + MATCH_END + "</para>", result);

            seq = xquery.execute("//para[ngram:contains(., 'aaa aaa')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "aaa aaa" + MATCH_END
                + " aaa</para>", result);

            seq = xquery.execute("//para[ngram:ends-with(., 'aaa aaa')]", null, AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>aaa " + MATCH_START + "aaa aaa" + MATCH_END + "</para>", result);

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void wildcardMatch() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF1, XML);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            Sequence seq = xquery.execute("//para[ngram:wildcard-contains(., 'double.*match')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            String result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert
                .assertEquals("<para>" + MATCH_START + "double match double match" + MATCH_END + "</para>", result);

            seq = xquery.execute("//para[ngram:wildcard-contains(., 'paragraph.*content\\.')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some " + MATCH_START + "paragraph with " + MATCH_END + "<hi>" + MATCH_START
                + "mixed" + MATCH_END + "</hi>" + MATCH_START + " content." + MATCH_END
                + "</para>", result);

            String wildcardQuery = "...with.*[tn].*ele.ent[sc].*";
            seq = xquery
.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>another paragra" + MATCH_START + "ph with " + MATCH_END + "<note><hi>"
                + MATCH_START + "nested" + MATCH_END + "</hi>" + MATCH_START + " inner" + MATCH_END + "</note>"
                + MATCH_START + " elements." + MATCH_END + "</para>", result);

            XpathEngine xpe = XMLUnit.newXpathEngine();
            NodeList matches = xpe.getMatchingNodes("//exist:match", XMLUnit.buildControlDocument(result));
            StringBuilder m = new StringBuilder();
            for (int i = 0; i < matches.getLength(); i++)
                m.append(matches.item(i).getTextContent());
            String match = m.toString();
            System.out.println("MATCH: " + match);

            assertMatches(wildcardQuery, match);

            wildcardQuery = "\\*.*\\?";
            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>Where did all the " + MATCH_START + "*s go?" + MATCH_END + "</para>", result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);
            assertMatches(wildcardQuery, match);

            wildcardQuery = ".est[][?]tes.";

            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "test]test" + MATCH_END + " " + MATCH_START + "test[test"
                + MATCH_END + " " + MATCH_START + "test?test" + MATCH_END + "</para>", result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);

            seq = xquery.execute("//para[ngram:wildcard-contains(., '^" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "test]test" + MATCH_END + " test[test test?test</para>",
                result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);

            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "$')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>test]test test[test " + MATCH_START + "test?test" + MATCH_END + "</para>",
                result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);

            wildcardQuery = "^aaa.aaa$";
            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "aaacaaa" + MATCH_END + "</para>", result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);
            assertMatches(wildcardQuery, match);

            wildcardQuery = ".+simple";
            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "a simple" + MATCH_END + " paragraph</para>", result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);
            assertMatches(wildcardQuery, match);

            wildcardQuery = "a s.?i.?m.?p.?l.?e.?";
            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "a simple " + MATCH_END + "paragraph</para>", result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);
            assertMatches(wildcardQuery, match);

            wildcardQuery = "a s.?i.?m.?p.?l.?e.?";
            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            result = queryResult2String(broker, seq, 0);
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>" + MATCH_START + "a simple " + MATCH_END + "paragraph</para>", result);

            match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
            System.out.println("MATCH: " + match);
            assertMatches(wildcardQuery, match);

            wildcardQuery = "b.{3,6}c";
            seq = xquery.execute("//para[ngram:wildcard-contains(., '" + wildcardQuery + "')]", null,
                AccessContext.TEST);
            assertNotNull(seq);
            assertEquals(2, seq.getItemCount());

            for (int i = 0; i < 2; i++) {
                result = queryResult2String(broker, seq, i);
                System.out.println("RESULT: " + result);

                match = xpe.evaluate("//exist:match", XMLUnit.buildControlDocument(result));
                System.out.println("MATCH: " + match);
                assertMatches(wildcardQuery, match);
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    private static void assertMatches(String regex, String actual) {
        assertTrue("actual value " + actual + " does not match " + regex, actual.matches(regex));
    }

    @Test
    public void smallStrings() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF3, XML2);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            String[] strings = new String[] { "龍", "龍護", "曰龍護", "名曰龍護" };
            for (int i = 0; i < strings.length; i++) {
                Sequence seq = xquery.execute(
                        "declare namespace tei=\"http://www.tei-c.org/ns/1.0\";\n" +
                        "//tei:p[ngram:contains(., '" + strings[i] + "')]",
                        null, AccessContext.TEST);
                assertNotNull(seq);
                assertEquals(1, seq.getItemCount());
                String result = queryResult2String(broker, seq, 0);
                System.out.println("RESULT: " + result);

                XMLAssert.assertXpathEvaluatesTo(i < 2 ? "2" : "1", "count(//exist:match)", result);
                XMLAssert.assertXpathExists("//exist:match[text() = '" + strings[i] + "']", result);
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @Test
    public void constructedNodes() {
        DBBroker broker = null;
        try {
            configureAndStore(CONF3, XML2);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);

            String[] strings = new String[] { "龍", "龍護", "曰龍護", "名曰龍護" };
            for (int i = 0; i < strings.length; i++) {
                Sequence seq = xquery.execute(
                        "declare namespace tei=\"http://www.tei-c.org/ns/1.0\";\n" +
                        "for $para in //tei:p[ngram:contains(., '" + strings[i] + "')]\n" +
                        "return\n" +
                        "   <match>{$para}</match>",
                        null, AccessContext.TEST);
                assertNotNull(seq);
                assertEquals(1, seq.getItemCount());
                String result = queryResult2String(broker, seq, 0);
                System.out.println("RESULT: " + result);

                XMLAssert.assertXpathEvaluatesTo(i < 2 ? "2" : "1", "count(//exist:match)", result);
                XMLAssert.assertXpathExists("//exist:match[text() = '" + strings[i] + "']", result);
            }

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

    @BeforeClass
    public static void startDB() throws DatabaseConfigurationException, EXistException {
        final File confFile = ConfigurationHelper.lookup("conf.xml");
        final Configuration config = new Configuration(confFile.getAbsolutePath());
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        HashMap<String, String> m = new HashMap<String, String>();
        m.put("tei", "http://www.tei-c.org/ns/1.0");
        m.put("exist", "http://exist.sourceforge.net/NS/exist");
        NamespaceContext ctx = new SimpleNamespaceContext(m);
        XMLUnit.setXpathNamespaceContext(ctx);
    }

    @AfterClass
    public static void closeDB() throws EXistException {
        final BrokerPool pool = BrokerPool.getInstance();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            Collection config = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            assertNotNull(config);
            broker.removeCollection(transaction, config);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        BrokerPool.stopAll(false);
    }

    private void configureAndStore(String config, String xml) {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, config);

            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test_matches.xml"), xml);
            assertNotNull(info);
            root.store(transaction, broker, info, xml, false);
            
            transact.commit(transaction);
        } catch (Exception e) {
        	e.printStackTrace();
        	fail(e.getMessage());
        }
    }

    private String queryResult2String(DBBroker broker, Sequence seq, int index) throws SAXException, XPathException {
        Properties props = new Properties();
        props.setProperty(OutputKeys.INDENT, "no");
        props.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperties(props);
        return serializer.serialize((NodeValue) seq.itemAt(index));
    }
}

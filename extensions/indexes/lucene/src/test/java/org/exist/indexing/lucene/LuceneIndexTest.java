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
package org.exist.indexing.lucene;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;

import org.exist.EXistException;
import org.exist.Indexer;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;

import org.junit.*;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.exist.samples.Samples.SAMPLES;

public class LuceneIndexTest {

    protected static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    protected static String XUPDATE_END =
        "</xu:modifications>";

    private static final String XML1 =
            "<section>" +
            "   <head>The title in big letters</head>" +
            "   <p rend=\"center\">A simple paragraph with <hi>just</hi> text in it.</p>" +
            "   <p rend=\"right\">paragraphs with <span>mix</span><span>ed</span> content are <span>danger</span>ous.</p>" +
            "</section>";

    private static final String XML2 =
            "<test>" +
            "   <item id='1' attr='attribute'><description>Chair</description></item>" +
            "   <item id='2'><description>Table</description>\n<condition>good</condition></item>" +
            "   <item id='3'><description>Cabinet</description>\n<condition>bad</condition></item>" +
            "</test>";

    private static final String XML3 =
            "<section>" +
            "   <head>TITLE IN UPPERCASE LETTERS</head>" +
            "   <p>UPPERCASE PARAGRAPH</p>" +
            "</section>";

    private static final String XML4 =
            "<test><a>A X</a><b><c>B X</c> C</b></test>";

    private static final String XML5 =
            "<article>" +
            "   <head>The <b>title</b>of it</head>" +
            "   <p>A simple paragraph with <hi>highlighted</hi> text <note>and a note</note> " +
            "       in it.</p>" +
            "   <p>Paragraphs with <s>mix</s><s>ed</s> content are <s>danger</s>ous.</p>" +
            "   <p><note1>ignore</note1> <s2>warn</s2>ings</p>" +
            "</article>";

    private static final String XML6 =
            "<a>" +
            "   <b>AAA</b>" +
            "   <c>AAA</c>" +
            "   <b>AAA</b>" +
            "</a>";

    private static final String XML7 =
        "<section>" +
        "   <head>Query Test</head>" +
        "   <p>Eine wunderbare Heiterkeit hat meine ganze Seele eingenommen, gleich den " +
        "   süßen Frühlingsmorgen, die ich mit ganzem Herzen genieße. Ich bin allein und " +
        "   freue mich meines Lebens in dieser Gegend, die für solche Seelen geschaffen " +
        "   ist wie die meine. Ich bin so glücklich, mein Bester, so ganz in dem Gefühle " +
        "   von ruhigem Dasein versunken, daß meine Kunst darunter leidet.</p>" +
        "</section>";

    private static final String XML8 =
        "<a>" +
        "   <b att='att on b1'>AAA on b1</b>" +
        "   <b att='att on b2' attr='attr on b2'>AAA on b2</b>" +
        "   <bb><b att='att on b3' attr='attr on b3'>AAA on b3</b></bb>" +
        "   <c att='att on c1'>AAA on c1</c>" +
        "   <c>AAA on c2</c>" +
        "</a>";

    private static final String XML9 =
	    "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\">" +
	    "   <body>" +
            "      <p>erste aus haus maus zaus yaus raus qaus leisten</p>" +
            "      <p>ausser aus</p>" +
            "      <p>auf boden</p>" +
	    "   </body>" +
            "</TEI>";

    private static final String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <analyzer class=\"org.apache.lucene.analysis.core.SimpleAnalyzer\"/>" +
        "           <text match=\"/section/p\"/>" +
        "           <text qname=\"head\"/>" +
        "           <text qname=\"@rend\"/>" +
        "           <text qname=\"hi\"/>" +
        "           <text qname=\"LINE\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";

    private static final String COLLECTION_CONFIG2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <text qname=\"item\"/>" +
        "           <text match=\"//description\"/>" +
        "           <text qname=\"condition\"/>" +
        "           <text qname=\"@attr\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";

    private static final String COLLECTION_CONFIG3 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "	<index>" +
        "       <lucene>" +
        "           <analyzer id=\"whitespace\" class=\"org.apache.lucene.analysis.core.WhitespaceAnalyzer\"/>" +
        "           <text match=\"/section/head\" analyzer=\"whitespace\"/>" +
        "           <text match=\"//p\"/>" +
        "       </lucene>" +
        "	</index>" +
        "</collection>";

    private static final String COLLECTION_CONFIG4 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "	<index>" +
            "       <lucene>" +
            "           <text match=\"/test/a\"/>" +
            "           <text match=\"/test/b/*\"/>" +
            "       </lucene>" +
            "	</index>" +
            "</collection>";

    private static final String COLLECTION_CONFIG5 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <lucene>" +
            "           <text qname=\"article\">" +
            "               <ignore qname=\"note\"/>" +
            "               <inline qname=\"s\"/>" +
            "           </text>" +
            "           <text qname=\"p\">" +
            "               <ignore qname=\"note\"/>" +
            "               <inline qname=\"s\"/>" +
            "           </text>" +
            "           <text qname=\"head\"/>" +
            "           <ignore qname=\"note1\"/>" +
            "           <inline qname=\"s2\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static final String COLLECTION_CONFIG6 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <lucene>" +
            "           <text qname=\"b\"/>" +
            "           <text qname=\"c\" boost=\"2.0\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static final String COLLECTION_CONFIG7 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <lucene>" +
            "           <text qname='c'>" +
            "               <has-attribute qname='att' boost='30'/>" +
            "           </text>" +
            "           <text qname='b' boost='5'>" +
            "               <match-attribute qname='att' value='att on b2' boost='30'/>" +
            "               <match-attribute qname='attr' value='attr on b3' boost='5'/>" +
            "               <has-attribute qname='attr' boost='15'/>" +
            "           </text>" +
            "           <text qname='@att'>" +
            "               <match-sibling-attribute qname='attr' value='attr on b2' boost='2'/>" +
            "               <has-sibling-attribute qname='attr' boost='2'/>" +
            "           </text>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static final String COLLECTION_CONFIG8 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <lucene>" +
            "          <analyzer class=\"org.apache.lucene.analysis.standard.StandardAnalyzer\"/>" +
            "           <text qname=\"tei:p\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static Collection root;
    private Boolean savedConfig;

    @Test
    public void simpleQueries() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            checkIndex(docs, broker, new QName[] { new QName("head") }, "title", 1);
            final Occurrences[] o = checkIndex(docs, broker, new QName[]{new QName("p")}, "with", 1);
            assertEquals(2, o[0].getOccurrences());
            checkIndex(docs, broker, new QName[] { new QName("hi") }, "just", 1);
            checkIndex(docs, broker, null, "in", 1);

            final QName attrQN = new QName("rend", XMLConstants.NULL_NS_URI, ElementValue.ATTRIBUTE);
            checkIndex(docs, broker, new QName[] { attrQN }, null, 2);
            checkIndex(docs, broker, new QName[] { attrQN }, "center", 1);
            checkIndex(docs, broker, new QName[] { attrQN }, "right", 1);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "/section[ft:query(p, 'content')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/section[ft:query(p/@rend, 'center')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/section[ft:query(hi, 'just')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "/section[ft:query(p/*, 'just')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/section[ft:query(head/*, 'just')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        }
    }

    @Test
    public void moreElaborateQueries() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, QName.IllegalQNameException {
        final String XML10 =
                "<TEI>\n" +  // xmlns=\"http://www.tei-c.org/ns/1.0\">\n" +
                "   <teiHeader>\n" +
                "      <title type='t' xml:lang=\"Sa-Ltn\">       Buick             </title> \n" + // this should get indexed
                "      <title type='t'     lang=\"Sa-Ltn\">       Cadillac          </title> \n" + // this should not get indexed -- attribute name ns does not match
                "      <title type='t' xml:lang=\"En\"    >       Dodge             </title> \n" + // this should not get indexed -- attribute value does not match
                "      <title type='t'                    >       Ford              </title> \n" + // this should not get indexed -- attribute is entirely missing
                "      <title type='t' xml:lang=\"Sa-Ltn\"> <tag> ABuick    </tag>  </title> \n" + // this should get indexed
                "      <title type='t'     lang=\"Sa-Ltn\"> <tag> ACadillac </tag>  </title> \n" + // this should not get indexed -- attribute name ns does not match
                "      <title type='t' xml:lang=\"En\"    > <tag> ADodge    </tag>  </title> \n" + // this should not get indexed -- attribute value does not match
                "      <title type='t'                    > <tag> AFord     </tag>  </title> \n" + // this should not get indexed -- attribute is entirely missing
                "   </teiHeader>\n" +
                "   <text>\n" +
                "       <group>\n" +
                "           <text>Nested</text>\n" +
                "       </group>\n" +
                "   </text>\n" +
                "</TEI>";

        final String COLLECTION_CONFIG10 =
                "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">\n" +
                "    <index>\n" +
                "        <!-- Lucene indexes -->\n" +
                "        <lucene diacritics='no'>\n" +
                "            <analyzer class='org.apache.lucene.analysis.standard.StandardAnalyzer'/>\n" +
                "            <text match=\"//title[@xml:lang='Sa-Ltn']\"/>\n" +
                "            <text match=\"/TEI/text\"><ignore qname=\"text\"/></text>\n" +
                "        </lucene> \n" +
                "    </index>\n" +
                "</collection>";


        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG10, XML10, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {


            // unbeknownst to me, the test on the next line fails if the literal "buick" is replaced with "Buick":
            final Occurrences[] o1 = checkIndex(docs, broker, new QName[]{new QName("title")}, "buick", 1);
            final Occurrences[] o2 = checkIndex(docs, broker, new QName[]{new QName("title")}, "cadillac", 0);
            final Occurrences[] o3 = checkIndex(docs, broker, new QName[]{new QName("title")}, "dodge", 0);
            final Occurrences[] o4 = checkIndex(docs, broker, new QName[]{new QName("title")}, "ford", 0);

            final Occurrences[] p1 = checkIndex(docs, broker, new QName[]{new QName("title")}, "abuick", 1);
            final Occurrences[] p2 = checkIndex(docs, broker, new QName[]{new QName("title")}, "acadillac", 0);
            final Occurrences[] p3 = checkIndex(docs, broker, new QName[]{new QName("title")}, "adodge", 0);
            final Occurrences[] p4 = checkIndex(docs, broker, new QName[]{new QName("title")}, "aford", 0);
            // nested <text> should be ignored and not indexed by match="/TEI/text"
            final Occurrences[] p5 = checkIndex(docs, broker, new QName[]{new QName("text")}, "nested", 0);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq;

            seq = xquery.execute(broker, "//.[ft:query(title, 'Buick')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//.[ft:query(title, 'Cadillac')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "//.[ft:query(title, 'Dodge')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "//.[ft:query(title, 'Ford')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "//.[ft:query(title, 'ABuick')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//.[ft:query(title, 'ACadillac')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "//.[ft:query(title, 'ADodge')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "//.[ft:query(title, 'AFord')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        }
    }

    @Test
    public void configuration() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG4, XML4, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            checkIndex(docs, broker, new QName[] { new QName("a") }, "x", 1);
            checkIndex(docs, broker, new QName[] { new QName("c") }, "x", 1);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "/test[ft:query(a, 'x')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/test[ft:query(.//c, 'x')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/test[ft:query(b, 'x')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        }
    }

    @Test
    public void inlineAndIgnore() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG5, XML5, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            checkIndex(docs, broker, new QName[] { new QName("head") }, "title", 1);
            checkIndex(docs, broker, new QName[] { new QName("p") }, "simple", 1);
            checkIndex(docs, broker, new QName[] { new QName("p") }, "mixed", 1);
            checkIndex(docs, broker, new QName[] { new QName("p") }, "dangerous", 1);
            checkIndex(docs, broker, new QName[] { new QName("p") }, "note", 0);
            checkIndex(docs, broker, new QName[] { new QName("p") }, "ignore", 0);
            checkIndex(docs, broker, new QName[]{new QName("p")}, "warnings", 1);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "/article[ft:query(head, 'title')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(p, 'highlighted')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(p, 'mixed')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(p, 'mix')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(p, 'dangerous')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(p, 'ous')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(p, 'danger')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(p, 'note')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(., 'highlighted')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(., 'mixed')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(., 'dangerous')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(., 'warnings')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(., 'danger')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(., 'note')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            seq = xquery.execute(broker, "/article[ft:query(., 'ignore')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        }
    }

    @Test
    public void attributeMatch() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException, XPathException, ParserConfigurationException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG7, XML8, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            Sequence seq = xquery.execute(broker, "for $a in ft:query((//b|//c), 'AAA') order by ft:score($a) descending return xs:string($a)", null);
            assertNotNull(seq);
            assertEquals(5, seq.getItemCount());
            assertEquals("AAA on b2", seq.itemAt(0).getStringValue());
            assertEquals("AAA on c1", seq.itemAt(1).getStringValue());
            assertEquals("AAA on b3", seq.itemAt(2).getStringValue());
            assertEquals("AAA on b1", seq.itemAt(3).getStringValue());
            assertEquals("AAA on c2", seq.itemAt(4).getStringValue());

            // path: /a/b
            seq = xquery.execute(broker, "for $a in ft:query(/a/b, 'AAA') order by ft:score($a) descending return xs:string($a)", null);
            assertNotNull(seq);
            assertEquals(2, seq.getItemCount());
            assertEquals("AAA on b2", seq.itemAt(0).getStringValue());
            assertEquals("AAA on b1", seq.itemAt(1).getStringValue());

            seq = xquery.execute(broker, "for $a in ft:query(//@att, 'att') order by ft:score($a) descending return xs:string($a)", null);
            assertNotNull(seq);
            assertEquals(4, seq.getItemCount());
            assertEquals("att on b2", seq.itemAt(0).getStringValue());
            assertEquals("att on b3", seq.itemAt(1).getStringValue());
            assertEquals("att on b1", seq.itemAt(2).getStringValue());
            assertEquals("att on c1", seq.itemAt(3).getStringValue());


            // modify with xupdate and check if boosts are updated accordingly
            final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);

            // remove 'att' attribute from first c element: it gets no boost
	    	// also append an 'att' attribute on second c element which will
	    	// make the two switch order in the result sequence.
            String xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//c[1]/@att\"/>" +
                    "   <xu:append select=\"//c[2]\"><xu:attribute name=\"att\">att on c2</xu:attribute></xu:append>" +
                    XUPDATE_END;

            final Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);

            modifications[0].process(transaction);
            modifications[1].process(transaction);
            proc.reset();
            transact.commit(transaction);

            seq = xquery.execute(broker, "for $a in ft:query((//b|//c), 'AAA') order by ft:score($a) descending return xs:string($a)", null);
            assertNotNull(seq);
            assertEquals(5, seq.getItemCount());
            assertEquals("AAA on b2", seq.itemAt(0).getStringValue());
            assertEquals("AAA on c2", seq.itemAt(1).getStringValue());
            assertEquals("AAA on b3", seq.itemAt(2).getStringValue());
            assertEquals("AAA on b1", seq.itemAt(3).getStringValue());
            assertEquals("AAA on c1", seq.itemAt(4).getStringValue());

        }
    }

    @Test
    public void boosts() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException, XPathException {
        configureAndStore(COLLECTION_CONFIG6, XML6, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "for $a in ft:query((//b|//c), 'AAA') " +
                    "order by ft:score($a) descending return $a/local-name(.)", null);
            assertNotNull(seq);
            assertEquals(3, seq.getItemCount());
            assertEquals("c", seq.getStringValue());
        }
    }

    @Test
    public void queryTranslation() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException, XPathException {
        configureAndStore(COLLECTION_CONFIG1, XML7, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            final CompiledXQuery compiled = xquery.compile(broker, context, "declare variable $q external; " +
                    "ft:query(//p, parse-xml($q)/query)");

            context.declareVariable("q", "<query><term>heiterkeit</term></query>");
            Sequence seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                    "<query>" +
                            "   <bool>" +
                            "       <term>heiterkeit</term><term>blablabla</term>" +
                            "   </bool>" +
                            "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                    "<query>" +
                            "   <bool>" +
                            "       <term occur='should'>heiterkeit</term><term occur='should'>blablabla</term>" +
                            "   </bool>" +
                            "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <term occur='must'>heiterkeit</term><term occur='must'>blablabla</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <term occur='must'>heiterkeit</term><term occur='not'>herzen</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <phrase occur='must'>wunderbare heiterkeit</phrase><term occur='must'>herzen</term>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                    "<query>" +
                    "   <phrase slop='5'>heiterkeit seele eingenommen</phrase>" +
                    "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            // phrase with wildcards
            context.declareVariable("q",
                "<query>" +
                "   <phrase slop='5'><term>heiter*</term><term>se?nnnle*</term></phrase>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <wildcard>?eiter*</wildcard>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <fuzzy max-edits='2'>selee</fuzzy>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <bool>" +
                "       <fuzzy occur='must' max-edits='2'>selee</fuzzy>" +
                "       <wildcard occur='should'>bla*</wildcard>" +
                "   </bool>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <regex>heit.*keit</regex>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            context.declareVariable("q",
                "<query>" +
                "   <phrase><term>wunderbare</term><regex>heit.*keit</regex></phrase>" +
                "</query>");
            seq = xquery.execute(broker, compiled, null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
        }
    }

    @Test
    public void analyzers() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG3, XML3, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            checkIndex(docs, broker, new QName[] { new QName("head") }, "TITLE", 1);
            checkIndex(docs, broker, new QName[] { new QName("p") }, "uppercase", 1);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "/section[ft:query(p, 'UPPERCASE')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/section[ft:query(head, 'TITLE')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "/section[ft:query(head, 'title')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
        }
    }

    @Test
    public void MultiTermQueryRewriteMethod() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException, XPathException {
        configureAndStore(COLLECTION_CONFIG8, XML9, "test.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "declare namespace tei=\"http://www.tei-c.org/ns/1.0\";" +
            " for $expr in (\"au*\", \"ha*\", \"ma*\", \"za*\", \"ya*\", \"ra*\", \"qa*\")" +
            " let $query := <query><wildcard>{$expr}</wildcard></query>" +
            " return for $hit in //tei:p[ft:query(., $query)]" +
            " return util:expand($hit)//exist:match", null);
            assertNotNull(seq);
            assertEquals(10, seq.getItemCount());
            assertEquals("aus", seq.itemAt(0).getStringValue());

	    seq = xquery.execute(broker, "declare namespace tei=\"http://www.tei-c.org/ns/1.0\";" +
            " for $expr in (\"ha*\", \"ma*\")" +
            " let $query := <query><wildcard>{$expr}</wildcard></query>" +
            " return for $hit in //tei:p[ft:query(., $query)]" +
            " return util:expand($hit)//exist:match", null);
	    assertNotNull(seq);
            assertEquals(2 , seq.getItemCount());
            assertEquals("haus", seq.itemAt(0).getStringValue());

        }
    }

    @Test
    public void dropSingleDoc() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "dropDocument.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            root.removeXMLResource(transaction, broker, XmldbURI.create("dropDocument.xml"));
            transact.commit(transaction);

            checkIndex(docs, broker, null, null, 0);
        }
    }

    @Test
    public void dropDocuments() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException, XPathException {
        configureAndStore(COLLECTION_CONFIG1, SAMPLES.getShakespeareXmlSampleNames());
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            try(final Txn transaction = transact.beginTransaction()) {
                Sequence seq = xquery.execute(broker, "//LINE[ft:query(., 'bark')]", null);
                assertNotNull(seq);
                assertEquals(6, seq.getItemCount());

                root.removeXMLResource(transaction, broker, XmldbURI.create("r_and_j.xml"));
                transact.commit(transaction);

                seq = xquery.execute(broker, "//LINE[ft:query(., 'bark')]", null);
                assertNotNull(seq);
                assertEquals(3, seq.getItemCount());
            }

            try(final Txn transaction = transact.beginTransaction()) {
                root.removeXMLResource(transaction, broker, XmldbURI.create("hamlet.xml"));
                transact.commit(transaction);

                Sequence seq = xquery.execute(broker, "//LINE[ft:query(., 'bark')]", null);
                assertNotNull(seq);
                assertEquals(1, seq.getItemCount());
            }
        }
    }

    @Test
    public void removeCollection() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException, XPathException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, SAMPLES.getShakespeareXmlSampleNames());
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//SPEECH[ft:query(LINE, 'love')]", null);
            assertNotNull(seq);
            assertEquals(166, seq.getItemCount());

            broker.removeCollection(transaction, root);

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);

            root = null;

            checkIndex(docs, broker, null, null, 0);
        }
    }

    @Test
    public void reindex() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG1, XML1, "dropDocument.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            broker.reindexCollection(transaction, TestConstants.TEST_COLLECTION_URI);

            checkIndex(docs, broker, new QName[] { new QName("head") }, "title", 1);
            final Occurrences[] o = checkIndex(docs, broker, new QName[]{new QName("p")}, "with", 1);
            assertEquals(2, o[0].getOccurrences());
            checkIndex(docs, broker, new QName[] { new QName("hi") }, "just", 1);
            checkIndex(docs, broker, null, "in", 1);

            final QName attrQN = new QName("rend", XMLConstants.NULL_NS_URI, ElementValue.ATTRIBUTE);
            checkIndex(docs, broker, new QName[] { attrQN }, null, 2);
            checkIndex(docs, broker, new QName[] { attrQN }, "center", 1);

            transaction.commit();
        }
    }

    /**
     * Remove nodes from different levels of the tree and check if the index is
     * correctly updated.
     */
    @Test
    public void xupdateRemove() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, ParserConfigurationException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            checkIndex(docs, broker, new QName[] { new QName("description") }, "chair", 1);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 2);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ft:query(description, 'chair')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='2']/condition\"/>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 1);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 4);
            checkIndex(docs, broker, new QName[] { new QName("condition") }, "good", 0);
            checkIndex(docs, broker, new QName[] { new QName("item") }, "good", 0);
            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("description") }, "table", 1);
            assertEquals("table", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description") }, "cabinet", 1);
            assertEquals("cabinet", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "table", 1);
            assertEquals("table", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "cabinet", 1);
            assertEquals("cabinet", o[0].getTerm());

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='3']/description/text()\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='1']\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            o = checkIndex(docs, broker, new QName[] { new QName("description") }, null, 1);
            assertEquals("table", o[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("description") }, "chair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item") }, "chair", 0);

            transact.commit(transaction);
        }
    }

    /**
     * Remove nodes from different levels of the tree and check if the index is
     * correctly updated.
     */
    @Test
    public void xupdateInsert() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, ParserConfigurationException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Occurrences occur[] = checkIndex(docs, broker, new QName[] { new QName("description") }, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 5);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ft:query(description, 'chair')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            // Append to root node
            final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                XUPDATE_START +
                "   <xu:append select=\"/test\">" +
                "       <item id='4'><description>Armchair</description> <condition>bad</condition></item>" +
                "   </xu:append>" +
                XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 2);
            checkIndex(docs, broker, new QName[] { new QName("description") }, null, 4);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 6);

            o = checkIndex(docs, broker, new QName[] { new QName("condition") }, "bad", 1);
            assertEquals("bad", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "bad", 1);
            assertEquals("bad", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());

            // Insert before top element
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']\">" +
                    "           <item id='0'><description>Wheelchair</description> <condition>poor</condition></item>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("description") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 8);

            o = checkIndex(docs, broker, new QName[] { new QName("condition") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());

            // Insert after element
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']\">" +
                    "           <item id='1.1'><description>refrigerator</description> <condition>perfect</condition></item>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 4);
            checkIndex(docs, broker, new QName[] { new QName("description") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 10);

            o = checkIndex(docs, broker, new QName[] { new QName("condition") }, "perfect", 1);
            assertEquals("perfect", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("description") }, "refrigerator", 1);
            assertEquals("refrigerator", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "perfect", 1);
            assertEquals("perfect", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "refrigerator", 1);
            assertEquals("refrigerator", o[0].getTerm());

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']/description\">" +
                    "           <condition>average</condition>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 11);
            o = checkIndex(docs, broker, new QName[] { new QName("condition") }, "average", 1);
            assertEquals("average", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "average", 1);
            assertEquals("average", o[0].getTerm());

            // Insert before nested element
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']/description\">" +
                    "           <condition>awesome</condition>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 12);
            o = checkIndex(docs, broker, new QName[] { new QName("condition") }, "awesome", 1);
            assertEquals("awesome", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "awesome", 1);
            assertEquals("awesome", o[0].getTerm());

            // Overwrite attribute
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:append select=\"//item[@id = '1']\">" +
                    "           <xu:attribute name=\"attr\">abc</xu:attribute>" +
                    "       </xu:append>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            QName qnattr[] = { new QName("attr", XMLConstants.NULL_NS_URI, XMLConstants.DEFAULT_NS_PREFIX, ElementValue.ATTRIBUTE) };
            o = checkIndex(docs, broker, qnattr, null, 1);
            assertEquals("abc", o[0].getTerm());
            checkIndex(docs, broker, qnattr, "attribute", 0);

            transact.commit(transaction);
        }
    }

    @Test
    public void xupdateUpdate() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, ParserConfigurationException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Occurrences occur[] = checkIndex(docs, broker, new QName[] { new QName("description") }, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 5);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ft:query(description, 'chair')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            // Update element content
            final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description\">wardrobe</xu:update>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("description") }, "chair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item") }, "chair", 0);
            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("description") }, "wardrobe", 1);
            assertEquals("wardrobe", o[0].getTerm());

            // Update text node
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description/text()\">Wheelchair</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 5);
            checkIndex(docs, broker, new QName[] { new QName("description") }, "wardrobe", 0);
            checkIndex(docs, broker, new QName[] { new QName("item") }, "wardrobe", 0);
            o = checkIndex(docs, broker, new QName[] { new QName("description") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());

            // Update attribute value
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/@attr\">abc</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            final QName qnattr[] = { new QName("attr", XMLConstants.NULL_NS_URI, XMLConstants.DEFAULT_NS_PREFIX, ElementValue.ATTRIBUTE) };
            o = checkIndex(docs, broker, qnattr, null, 1);
            assertEquals("abc", o[0].getTerm());
            checkIndex(docs, broker, qnattr, "attribute", 0);

            transact.commit(transaction);
        }
    }

    @Test
    public void xupdateReplace() throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, LockException, IOException, XPathException, ParserConfigurationException, QName.IllegalQNameException {
        final DocumentSet docs = configureAndStore(COLLECTION_CONFIG2, XML2, "xupdate.xml");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            final Occurrences occur[] = checkIndex(docs, broker, new QName[] { new QName("description") }, "chair", 1);
            assertEquals("chair", occur[0].getTerm());
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 5);

            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ft:query(description, 'chair')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "<xu:replace select=\"//item[@id = '1']\">" +
                    "<item id='4'><description>Wheelchair</description> <condition>poor</condition></item>" +
                    "</xu:replace>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("condition") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("description") }, "chair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item") }, "chair", 0);
            Occurrences o[] = checkIndex(docs, broker, new QName[] { new QName("description") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("condition") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "wheelchair", 1);
            assertEquals("wheelchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "poor", 1);
            assertEquals("poor", o[0].getTerm());

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "<xu:replace select=\"//item[@id = '4']/description\">" +
                    "<description>Armchair</description>" +
                    "</xu:replace>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(docs, broker, new QName[] { new QName("description") }, null, 3);
            checkIndex(docs, broker, new QName[] { new QName("item") }, null, 6);
            checkIndex(docs, broker, new QName[] { new QName("description") }, "wheelchair", 0);
            checkIndex(docs, broker, new QName[] { new QName("item") }, "wheelchair", 0);
            o = checkIndex(docs, broker, new QName[] { new QName("description") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());
            o = checkIndex(docs, broker, new QName[] { new QName("item") }, "armchair", 1);
            assertEquals("armchair", o[0].getTerm());

            transact.commit(transaction);
         }
    }

    private DocumentSet configureAndStore(final String configuration, final String data, final String docName) throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            if (configuration != null) {
                final CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }

            final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(docName), data);
            assertNotNull(info);
            root.store(transaction, broker, info, data);

            docs.add(info.getDocument());
            transact.commit(transaction);
        }

        return docs;
    }

    private DocumentSet configureAndStore(String configuration, final String[] sampleNames) throws EXistException, CollectionConfigurationException, PermissionDeniedException, SAXException, TriggerException, LockException, IOException {

        final MutableDocumentSet docs = new DefaultDocumentSet();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            if (configuration != null) {
                final CollectionConfigurationManager mgr = pool.getConfigurationManager();
                mgr.addConfiguration(transaction, broker, root, configuration);
            }

            for (final String sampleName : sampleNames) {
                try (final InputStream is = SAMPLES.getShakespeareSample(sampleName)) {
                    final String sample = InputStreamUtil.readString(is, UTF_8);
                    final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(sampleName), sample);
                    assertNotNull(info);
                    root.store(transaction, broker, info, sample);
                    docs.add(info.getDocument());
                }
            }
            transact.commit(transaction);
        }

        return docs;
    }

    /** It really depends on the Analyzer used with the index,
     *  but probably you would like to have the 'term' argument all lower cased.
     *  @see <a href='https://sourceforge.net/p/exist/mailman/message/36436727/'>Help needed with a test case</a>
     */
    private Occurrences[] checkIndex(final DocumentSet docs, final DBBroker broker, final QName[] qn, final String term, final int expected) {
        final LuceneIndexWorker index = (LuceneIndexWorker)broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        final Map<String, Object> hints = new HashMap<>();
        if (term != null) {
            hints.put(OrderedValuesIndex.START_VALUE, term);
        }
        if (qn != null && qn.length > 0) {
            final List<QName> qnlist = new ArrayList<>(qn.length);
            qnlist.addAll(Arrays.asList(qn));
            hints.put(QNamedKeysIndex.QNAMES_KEY, qnlist);
        }
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        final Occurrences[] occur = index.scanIndex(context, docs, null, hints);
        assertEquals(expected, occur.length);
        return occur;
    }

    @Before
    public void setup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);

            final Configuration config = BrokerPool.getInstance().getConfiguration();
            savedConfig = (Boolean) config.getProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT);
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, Boolean.TRUE);
        }
    }

    @After
    public void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection collConfig = broker.getOrCreateCollection(transaction,
                XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"));
            assertNotNull(collConfig);
            broker.removeCollection(transaction, collConfig);

            if (root != null) {
                assertNotNull(root);
                broker.removeCollection(transaction, root);
            }
            transact.commit(transaction);

            final Configuration config = BrokerPool.getInstance().getConfiguration();
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, savedConfig);
        }
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                .set(Indexer.PROPERTY_SUPPRESS_WHITESPACE, "none")
                .put(Indexer.PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE, Boolean.TRUE)
                .build(),
            true,
            false);

    @AfterClass
    public static void cleanupDb() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id: LuceneMatchListenerTest.java 12494 2010-08-21 12:40:10Z shabanovd $
 */
package org.exist.indexing.lucene;

import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.search.CountFacetRequest;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.search.FacetResultNode;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.custommonkey.xmlunit.XMLAssert;
import org.exist.dom.DocumentSet;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import static org.junit.Assert.*;

import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class FacetMatchListenerTest extends FacetAbstractTest {

    private static String XML =
            "<root>" +
            "   <para>some paragraph with <hi>mixed</hi> content.</para>" +
            "   <para>another paragraph with <note><hi>nested</hi> inner</note> elements.</para>" +
            "   <para>a third paragraph with <term>term</term>.</para>" +
            "   <para>double match double match</para>" +
            "</root>";

    private static String XML1 =
            "<article>" +
            "   <head>The <b>title</b>of it</head>" +
            "   <p>A simple<note>sic</note> paragraph with <hi>highlighted</hi> text <note>and a note</note> to be ignored.</p>" +
            "   <p>Paragraphs with <s>mix</s><s>ed</s> content are <s>danger</s>ous.</p>" +
            "</article>";

    private static String CONF1 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "	<index>" +
            "		<fulltext default=\"none\">" +
            "		</fulltext>" +
            "   <text qname=\"para\"/>" +
            "	</index>" +
            "</collection>";

    private static String CONF2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "	<index>" +
        "		<fulltext default=\"none\">" +
        "		</fulltext>" +
        "       <text qname=\"para\"/>" +
        "       <text qname=\"term\"/>" +
        "	</index>" +
        "</collection>";

    private static String CONF3 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "	<index>" +
            "		<fulltext default=\"none\">" +
            "		</fulltext>" +
            "   <text qname=\"hi\"/>" +
            "	</index>" +
            "</collection>";

    private static String CONF4 =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "   <index xmlns:tei=\"http://www.tei-c.org/ns/1.0\">" +
            "       <fulltext default=\"none\" attributes=\"no\">" +
            "       </fulltext>" +
            "       <lucene>" +
            "           <text qname=\"p\">" +
            "               <ignore qname=\"note\"/>" +
            "           </text>" +
            "           <text qname=\"head\"/>" +
            "           <inline qname=\"s\"/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static String MATCH_START = "<exist:match xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
    private static String MATCH_END = "</exist:match>";

    private static Map<String, String> metas1 = new HashMap<String, String>();
    static {
        metas1.put("status", "draft");
    }

    private static Map<String, String> metas2 = new HashMap<String, String>();
    static {
        metas2.put("status", "final");
    }
    
    private void checkFacet2(List<FacetResult> facets) {
        assertEquals(1, facets.size());
        
        FacetResult facet = facets.get(0);
        assertEquals(1, facet.getNumValidDescendants());
        FacetResultNode node = facet.getFacetResultNode();
        assertEquals(0.0, node.value, 0.0001);
        assertEquals("status", node.label.toString());
        
        List<FacetResultNode> subResults = node.subResults;
        assertEquals(1, subResults.size());
        
        node = subResults.get(0);
        assertEquals(1.0, node.value, 0.0001);
        assertEquals("status/final", node.label.toString());
    }

    private void checkFacet(List<FacetResult> facets) {
        assertEquals(1, facets.size());
        
        FacetResult facet = facets.get(0);
        assertEquals(2, facet.getNumValidDescendants());
        FacetResultNode node = facet.getFacetResultNode();
        assertEquals(0.0, node.value, 0.0001);
        assertEquals("status", node.label.toString());
        
        List<FacetResultNode> subResults = node.subResults;
        assertEquals(2, subResults.size());
        
        node = subResults.get(0);
        assertEquals(1.0, node.value, 0.0001);
        assertEquals("status/final", node.label.toString());
        
        node = subResults.get(1);
        assertEquals(1.0, node.value, 0.0001);
        assertEquals("status/draft", node.label.toString());
    }

    /**
     * Test match highlighting for index configured by QName, e.g.
     * &lt;create qname="a"/&gt;.
     */
    @Test
    public void indexByQName() {
        DBBroker broker = null;
        try {
            DocumentSet docs = configureAndStore(CONF2,
                new Resource[] {
                    new Resource("test1.xml", XML, metas1),
                    new Resource("test2.xml", XML, metas2),
                    new Resource("test3.xml", XML1, metas2),
                });

            broker = db.get(db.getSecurityManager().getSystemSubject());
            
            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);

            List<FacetResult> results;
            String result;

            FacetSearchParams fsp = new FacetSearchParams(
                    new CountFacetRequest(new CategoryPath("status"), 10)
//                    new CountFacetRequest(new CategoryPath("Author"), 10)
            );
            
            CountAndCollect cb = new CountAndCollect();
            
            List<QName> qnames = new ArrayList<QName>();
            qnames.add(new QName("para", ""));

            //query without facet filter
            results = QueryNodes.query(worker, docs, qnames, 1, "mixed", fsp, null, cb);
            
            assertEquals(2, cb.count);
            
            for (int i = 0; i < 2; i++) {
                result = queryResult2String(broker, cb.set.get(0));
                System.out.println("RESULT: " + result);
                XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                        MATCH_END + "</hi> content.</para>", result);
            }

            checkFacet(results);

            cb.count = 0;
            
            //query with facet filter
            results = QueryNodes.query(worker, docs, qnames, 1, "mixed AND status:final", fsp, null, cb);
            
            assertEquals(1, cb.count);
            
            result = queryResult2String(broker, cb.set.get(0));
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                    MATCH_END + "</hi> content.</para>", result);

            checkFacet2(results);
            
            cb.count = 0;

//            seq = xquery.execute("//para[ft:query(., '+nested +inner +elements')]", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<para>another paragraph with <note><hi>" + MATCH_START + "nested" +
//                    MATCH_END + "</hi> " + MATCH_START +
//                    "inner" + MATCH_END + "</note> " + MATCH_START + "elements" + MATCH_END + ".</para>", result);
//
//            seq = xquery.execute("//para[ft:query(term, 'term')]", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<para>a third paragraph with <term>" + MATCH_START + "term" + MATCH_END +
//                    "</term>.</para>", result);
//
//            seq = xquery.execute("//para[ft:query(., '+double +match')]", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<para>" + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + "</para>", result);
//
//            seq = xquery.execute(
//                    "for $para in //para[ft:query(., '+double +match')] return\n" +
//                            "   <hit>{$para}</hit>", null, AccessContext.TEST);
//            assertNotNull(seq);
//            assertEquals(1, seq.getItemCount());
//            result = queryResult2String(broker, seq);
//            System.out.println("RESULT: " + result);
//            XMLAssert.assertEquals("<hit><para>" + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + " " + MATCH_START + "double" + MATCH_END + " " +
//                    MATCH_START + "match" + MATCH_END + "</para></hit>", result);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }

    private String queryResult2String(DBBroker broker, NodeValue node) throws SAXException, XPathException {
        Properties props = new Properties();
        props.setProperty(OutputKeys.INDENT, "no");
        props.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperties(props);
        return serializer.serialize(node);
    }
    
    protected class CountAndCollect implements SearchCallback<NodeProxy> {
        
        NodeSet set = new NewArrayNodeSet();

        int count = 0;
        
        @Override
        public void found(NodeProxy node, float score) {
            count++;
            
            set.add(node);
        }
        
    }

}

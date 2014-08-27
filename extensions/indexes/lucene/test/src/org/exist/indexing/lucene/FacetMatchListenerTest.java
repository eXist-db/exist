/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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

import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.AtomicReader;
import org.custommonkey.xmlunit.XMLAssert;
import org.exist.collections.Collection;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;

import static org.junit.Assert.*;

import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class FacetMatchListenerTest extends FacetAbstract {

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

    private static String CONF2 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "	<index>" +
        "       <text qname=\"para\"/>" +
        "       <text qname=\"term\"/>" +
        "	</index>" +
        "</collection>";

    private static String CONF3 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "       <index>" +
        "       <text qname=\"para\"/>" +
        "       <text qname=\"term\"/>" +
        "       </index>" +
        "</collection>";

    private static String MATCH_START = "<exist:match xmlns:exist=\"http://exist.sourceforge.net/NS/exist\">";
    private static String MATCH_END = "</exist:match>";

    private static Map<String, String> metas1 = new HashMap<>();
    static {
        metas1.put("status", "draft");
    }

    private static Map<String, String> metas2 = new HashMap<>();
    static {
        metas2.put("status", "final");
    }
    
    private void checkFacet2(FacetResult facet) {

        assertEquals(1, facet.childCount);

        assertEquals(1.0, facet.value.doubleValue(), 0.0001);
        assertEquals("status", facet.dim);

        LabelAndValue[] subResults = facet.labelValues;
        assertEquals(1, subResults.length);

        LabelAndValue node = subResults[0];
        assertEquals(1.0, node.value.doubleValue(), 0.0001);
        assertEquals("final", node.label);
    }

    private void checkFacet(FacetResult facet) {

        assertEquals(2, facet.childCount);

        assertEquals(2.0, facet.value.doubleValue(), 0.0001);
        assertEquals("status", facet.dim);

        LabelAndValue[] subResults = facet.labelValues;
        assertEquals(2, subResults.length);

        LabelAndValue node = subResults[0];
        assertEquals(1.0, node.value.doubleValue(), 0.0001);
        assertEquals("draft", node.label);
        
        node = subResults[1];
        assertEquals(1.0, node.value.doubleValue(), 0.0001);
        assertEquals("final", node.label);
    }

    private void printFacet(List<FacetResult> facets) {
        for (FacetResult facet : facets) {
            System.out.println(facet);
        }
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

            Facets results;
            String result;

            FacetsConfig facetsConfig = new FacetsConfig();
            facetsConfig.setHierarchical("status", true); //"Author"

            CountAndCollect cb = new CountAndCollect();
            
            List<QName> qnames = new ArrayList<>();
            qnames.add(new QName("para", ""));

            //query without facet filter
            results = QueryNodes.query(worker, docs, qnames, 1, "mixed", facetsConfig, null, cb);
            
            assertEquals(2, cb.count);
            //assertEquals(2, cb.total);
            
            for (int i = 0; i < 2; i++) {
                result = queryResult2String(broker, cb.set.get(0));
                System.out.println("RESULT: " + result);
                XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                        MATCH_END + "</hi> content.</para>", result);
            }

            FacetResult facet = results.getTopChildren(2, STATUS);

            //printFacet(results.getAllDims(10));
            checkFacet(facet);

            cb.reset();
            
            //query with facet filter
            results = QueryNodes.query(worker, docs, qnames, 1, "mixed AND status:final", facetsConfig, null, cb);
            
            assertEquals(1, cb.count);
            //assertEquals(1, cb.total);
            
            result = queryResult2String(broker, cb.set.get(0));
            System.out.println("RESULT: " + result);
            XMLAssert.assertEquals("<para>some paragraph with <hi>" + MATCH_START + "mixed" +
                    MATCH_END + "</hi> content.</para>", result);

            facet = results.getTopChildren(2, STATUS);

            checkFacet2(facet);
            
            cb.reset();

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
    
    //@Test
	public void testName() throws Exception {
    	final DBBroker _broker;
        DBBroker broker = null;
        try {
        	_broker = broker = db.get(db.getSecurityManager().getSystemSubject());
        	
        	MutableDocumentSet docs = new DefaultDocumentSet(1031);

            Collection collection = broker.getCollection(
        		XmldbURI.xmldbUriFor("/db/system/config/db/organizations/test-org/repositories")
    		);
            assertNotNull(collection);
            collection.allDocs(broker, docs, true);
	        
	        final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
	
            FacetsConfig facetsConfig = new FacetsConfig();
            facetsConfig.setHierarchical("status", true); //"Author"

	        SearchCallback<NodeProxy> cb = new SearchCallback<NodeProxy>() {

				@Override
				public void totalHits(Integer number) {
				}

				@Override
				public void found(AtomicReader reader, int docNum, NodeProxy element, float score) {
					try {
						System.out.println(
							queryResult2String(_broker, element, 10, LuceneMatchChunkListener.CHUNK)
						);
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
	        	
	        };
	        
	        System.out.println("RUN QUERY");
	        
//	        List<QName> qnames = new ArrayList<QName>();
//	        qnames.add(new QName("para", ""));
	
	        //query without facet filter
            Facets results = QueryNodes.query(worker, docs, null, 1, "admin*", facetsConfig, null, cb);

            System.out.println(results);

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
    
    protected class CountAndCollect extends Counter<NodeProxy> {
        
        NodeSet set = new NewArrayNodeSet();

        @Override
        public void found(AtomicReader reader, int docNum, NodeProxy node, float score) {
        	super.found(reader, docNum, node, score);
            
            set.add(node);
        }
        
        public void reset() {
        	super.reset();
        	
        	set = new NewArrayNodeSet();
        }
        
    }
    
    private String queryResult2String(DBBroker broker, NodeProxy proxy, int chunkOffset, byte mode) throws SAXException, XPathException {
        Properties props = new Properties();
        props.setProperty(OutputKeys.INDENT, "no");
        
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        
        LuceneMatchChunkListener highlighter = new LuceneMatchChunkListener(getLuceneIndex(), chunkOffset, mode);
        highlighter.reset(broker, proxy);
        
        final StringWriter writer = new StringWriter();
        
        SerializerPool serializerPool = SerializerPool.getInstance();
        SAXSerializer xmlout = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
        try {
        	//setup pipes
			xmlout.setOutput(writer, props);
			
			highlighter.setNextInChain(xmlout);
			
			serializer.setReceiver(highlighter);
			
			//serialize
	        serializer.toReceiver(proxy, false, true);
	        
	        //get result
	        return writer.toString();
        } finally {
        	serializerPool.returnObject(xmlout);
        }
    }
    
    private LuceneIndex getLuceneIndex() {
        return (LuceneIndex) db.getIndexManager().getIndexById(LuceneIndex.ID);
    }
}

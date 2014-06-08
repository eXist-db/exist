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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.search.CountFacetRequest;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.search.FacetResultNode;
import org.apache.lucene.facet.taxonomy.CategoryPath;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;

public class FacetIndexTest extends FacetAbstract {
	
	private final static String CREATED = "created";
	private final static String STATUS = "status";

    protected static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    protected static String XUPDATE_END =
        "</xu:modifications>";
    
    private static String XML1 =
            "<article>" +
            "   <head>The <b>title</b>of it</head>" +
            "   <p>A simple paragraph with <hi>highlighted</hi> text <note>and a note</note> " +
            "       in it.</p>" +
            "   <p>Paragraphs with <s>mix</s><s>ed</s> content are <s>danger</s>ous.</p>" +
            "   <p><note1>ignore</note1> <s2>warn</s2>ings</p>" +
            "   <p>Another simple paragraph.</p>" +
            "</article>";
    
    private static String BINARY1 = "A simple paragraph with highlighted text and a note in it.";

    private static String COLLECTION_CONFIG5 =
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
    
    private static String COLLECTION_CONFIG6 =
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
            "   <index xmlns:tei='http://www.tei-c.org/ns/1.0'>" +
            "       <lucene>" +
            "           <text qname='article'>" +
            "               <ignore qname='note'/>" +
            "               <inline qname='s'/>" +
            "           </text>" +
            "           <text qname='p'>" +
            "               <ignore qname='note'/>" +
            "               <inline qname='s'/>" +
            "           </text>" +
            "           <text qname='head'/>" +
            "           <ignore qname='note1'/>" +
            "           <inline qname='s2'/>" +
            "           <fieldType id='"+STATUS+"' store='no' tokenized='no'/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static String COLLECTION_CONFIG7 =
            "<collection xmlns='http://exist-db.org/collection-config/1.0'>" +
            "   <index xmlns:tei='http://www.tei-c.org/ns/1.0'>" +
            "       <lucene>" +
            "           <text qname='article'>" +
            "               <ignore qname='note'/>" +
            "               <inline qname='s'/>" +
            "           </text>" +
            "           <text qname='p'>" +
            "               <ignore qname='note'/>" +
            "               <inline qname='s'/>" +
            "           </text>" +
            "           <text qname='head'/>" +
            "           <ignore qname='note1'/>" +
            "           <inline qname='s2'/>" +
            "           <fieldType id='"+STATUS+"' symbolized='yes'/>" +
            "       </lucene>" +
            "   </index>" +
            "</collection>";

    private static Map<String, String> metas1 = new HashMap<String, String>();
    static {
        metas1.put(STATUS, "draft");
        metas1.put(CREATED, "20130803");
    }

    private static Map<String, String> metas2 = new HashMap<String, String>();
    static {
        metas2.put(STATUS, "final");
        metas2.put(CREATED, "20130805");
    }

    private static Map<String, String> metas3 = new HashMap<String, String>();
    static {
        metas3.put(STATUS, "draft");
        metas3.put(CREATED, "20130807");
    }

    private static Map<String, String> metas4 = new HashMap<String, String>();
    static {
        metas4.put(STATUS, "final");
        metas4.put(CREATED, "20130811");
    }

    private static Map<String, String> metas5 = new HashMap<String, String>();
    static {
        metas5.put(STATUS, "in review");
        metas5.put(CREATED, "20130901");
    }

    private static Map<String, String> metas6 = new HashMap<String, String>();
    static {
        metas6.put(STATUS, "in draft");
        metas6.put(CREATED, "20130901");
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

    @Test
    public void inlineAndIgnore() {
        System.out.println("Test simple queries ...");
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG5, 
                new Resource[] {
                    new Resource("test1.xml", XML1, metas1),
                    new Resource("test2.xml", XML1, metas2),
                });
        
        DBBroker broker = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
            FacetSearchParams fsp = new FacetSearchParams(
                new CountFacetRequest(new CategoryPath(STATUS), 10)
            );
            
            Counter<DocumentImpl> cb = new Counter<DocumentImpl>();
            
            List<QName> qnames = new ArrayList<QName>();
            qnames.add(new QName("head", ""));
            List<FacetResult> results = QueryDocuments.query(worker, docs, qnames, "title", fsp, null, cb);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet(results);
            
            cb.reset();

            //Lucene query
            QName qname = new QName("head", "");
            
            String field = LuceneUtil.encodeQName(new QName("head", ""), db.getSymbols());
            
            Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);

            QueryParser parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

            //worker.setOptions(options, parser);

            Query query = parser.parse("title");
            
            
            results = QueryDocuments.query(worker, docs, query, fsp, cb);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet(results);

            cb.reset();
            
            //check document filtering
            qnames = new ArrayList<QName>();
            qnames.add(new QName("p", ""));
            results = QueryDocuments.query(worker, docs, qnames, "paragraph", fsp, null, cb);
            
            for (FacetResult result : results) {
                System.out.println(result.toString());
            }
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet(results);
            
            cb.reset();
            
            Sort sort = new Sort(new SortField(CREATED, SortField.Type.STRING, true));
            
            BooleanQuery bq = new  BooleanQuery();
            bq.add(new TermQuery(new Term(STATUS, "draft")), BooleanClause.Occur.SHOULD);
            bq.add(new TermQuery(new Term(STATUS, "final")), BooleanClause.Occur.SHOULD);
            
            results = QueryDocuments.query(worker, docs, bq, fsp, cb, 100, sort);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);

            checkFacet(results);

            cb.reset();

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }
    
    @Test
    public void counterBinary() {
        System.out.println("Test counterBinary ...");
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG5, 
                new Resource[] {
                    new Resource("BINARY", "test1.txt", BINARY1, metas1),
                    new Resource("BINARY", "test2.txt", BINARY1, metas2),
                });
        
        DBBroker broker = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
            FacetSearchParams fsp = new FacetSearchParams(
                new CountFacetRequest(new CategoryPath(STATUS), 10)
            );
            
            Counter<DocumentImpl> cb = new Counter<DocumentImpl>();
            
            Sort sort = new Sort(new SortField(CREATED, SortField.Type.STRING, true));
            
            BooleanQuery bq = new  BooleanQuery();
            bq.add(new TermQuery(new Term(STATUS, "draft")), BooleanClause.Occur.SHOULD);
            bq.add(new TermQuery(new Term(STATUS, "final")), BooleanClause.Occur.SHOULD);
            
            List<FacetResult> results = QueryDocuments.query(worker, docs, bq, fsp, cb, 100, sort);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet(results);
            
            cb.reset();

            //Lucene query
//            QName qname = new QName("head", "");
//            
//            String field = LuceneUtil.encodeQName(new QName("head", ""), db.getSymbols());
//            
//            Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);
//
//            QueryParser parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);
//
//            //worker.setOptions(options, parser);
//
//            Query query = parser.parse("title");
//            
//            
//            results = QueryDocuments.query(worker, docs, query, fsp, cb);
//            
//            assertEquals(2, cb.count);
//            assertEquals(2, cb.total);
//            
//            checkFacet(results);
//
//            cb.reset();
//            
//            //check document filtering
//            qnames = new ArrayList<QName>();
//            qnames.add(new QName("p", ""));
//            results = QueryDocuments.query(worker, docs, qnames, "paragraph", fsp, null, cb);
//            
//            for (FacetResult result : results) {
//                System.out.println(result.toString());
//            }
//            
//            assertEquals(2, cb.count);
//            assertEquals(2, cb.total);
//            
//            checkFacet(results);
//            
//            cb.reset();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }

    private void checkFacet2(List<FacetResult> facets) {
        assertEquals(2, facets.size());
        
        FacetResult facet = facets.get(0);
        assertEquals(1, facet.getNumValidDescendants());
        FacetResultNode node = facet.getFacetResultNode();
        assertEquals(0.0, node.value, 0.0001);
        assertEquals("status", node.label.toString());
        
        List<FacetResultNode> subResults = node.subResults;
        assertEquals(1, subResults.size());
        
        node = subResults.get(0);
        assertEquals(2.0, node.value, 0.0001);
        assertEquals("status/draft", node.label.toString());

        facet = facets.get(1);
        assertEquals(1, facet.getNumValidDescendants());
        node = facet.getFacetResultNode();
        assertEquals(2.0, node.value, 0.0001);
        assertEquals("eXist:meta-type/application", node.label.toString());
        
        subResults = node.subResults;
        assertEquals(1, subResults.size());
        
        node = subResults.get(0);
        assertEquals(2.0, node.value, 0.0001);
        assertEquals("eXist:meta-type/application/xml", node.label.toString());
    }

    @Test
    public void sorting() {
        System.out.println("Test sorting queries ...");
        DocumentSet docs = configureAndStore(COLLECTION_CONFIG5, 
                new Resource[] {
                    new Resource("test1.xml", XML1, metas1),
                    new Resource("test2.xml", XML1, metas2),
                    new Resource("test3.xml", XML1, metas3),
                    new Resource("test4.xml", XML1, metas4),
                });
        
        DBBroker broker = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
            FacetSearchParams fsp = new FacetSearchParams(
                new CountFacetRequest(new CategoryPath(STATUS), 10),
                new CountFacetRequest(new CategoryPath("eXist:meta-type"), 10),
                new CountFacetRequest(new CategoryPath("eXist:meta-type","application"), 10)
//                new CountFacetRequest(new CategoryPath("Author"), 10)
            );
            
            Sort sort = new Sort(new SortField(CREATED, SortField.Type.STRING, true));
            
            Counter<DocumentImpl> cb = new Counter<DocumentImpl>();
            
            List<QName> qnames = worker.getDefinedIndexes(null);
            
            BooleanQuery bq = new  BooleanQuery();

            //set filter-like on meta
            bq.add(new TermQuery(new Term(STATUS, "draft")), BooleanClause.Occur.MUST);
            
            String searchText = "paragraph";
            for (QName qname : qnames) {
            	final String field = LuceneUtil.encodeQName(qname, db.getSymbols());
            	
            	//System.out.println(qname);
            	
            	bq.add(new PrefixQuery(new Term(field, searchText)), BooleanClause.Occur.SHOULD);
            }

            List<FacetResult> results = QueryDocuments.query(worker, docs, bq, fsp, cb, 5, sort);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            for (FacetResult result : results) {
            	System.out.println(result.toString());
            }
            
            checkFacet2(results);
            
            System.out.println("================");
            
            cb.reset();

            sort = new Sort(new SortField(CREATED, SortField.Type.STRING, false));
            
            results = QueryDocuments.query(worker, docs, bq, fsp, cb, 5, sort);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet2(results);

            System.out.println("================");
            
            cb.reset();

            sort = new Sort(new SortField(CREATED, SortField.Type.STRING, false));
            
            results = QueryDocuments.query(worker, docs, bq, fsp, cb, 1, sort);
            
            assertEquals(1, cb.count);
            assertEquals(1, cb.total);
            
            //checkFacet2(results);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }
    
    @Test
    public void testOrder() {
        System.out.println("Test order ...");
        
        configureAndStore(COLLECTION_CONFIG6, 
                new Resource[] {
                    new Resource("test1.xml", XML1, metas1),
                    new Resource("test2.xml", XML1, metas2),
                    new Resource("test3.xml", XML1, metas3),
                    new Resource("test4.xml", XML1, metas4),
                    new Resource("test5.xml", XML1, metas5),
                    new Resource("test6.xml", XML1, metas6),
                });
        
        final ArrayList<DocumentImpl> myHits = new ArrayList<DocumentImpl>();
        
        Counter<DocumentImpl> cb = new Counter<DocumentImpl>() {
            @Override
            public void found(AtomicReader reader, int docNum, DocumentImpl document, float score) {
            	super.found(reader, docNum, document, score);
                myHits.add( document );
                //System.out.println("Found! uri (IN TEST QUERY): " + document.getURI().toASCIIString() + " " + score);
            }
            
            public void reset() {
            	super.reset();
            	
            	myHits.clear();
            }
        };
        
        DBBroker broker = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
            FacetSearchParams fsp = new FacetSearchParams(
                new CountFacetRequest(new CategoryPath(STATUS), 100)
            );

            MutableDocumentSet docs = new DefaultDocumentSet(1031);
            broker.getCollection(XmldbURI.xmldbUriFor("/db")).allDocs(broker, docs, true);

            List<QName> qNames = new ArrayList<QName>();
            qNames = worker.getDefinedIndexes(qNames);


            // Parse the query with no default field:
            Analyzer analyzer = new StandardAnalyzer(LuceneIndex.LUCENE_VERSION_IN_USE);

            String[] fields = new String[qNames.size()];
            int i = 0;
            for (QName qName : qNames) {
                fields[i] = LuceneUtil.encodeQName(qName, db.getSymbols());
                i++;
            }
            MultiFieldQueryParser parser = new MultiFieldQueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, fields, analyzer);
            Query query = parser.parse("a*");


            // Sort by status & creation date:
            List<FacetResult> results = QueryDocuments.query(worker, docs, query, fsp, cb, 
            		200, 
            		new org.apache.lucene.search.Sort(
            				new SortField(STATUS, SortField.Type.STRING, true),
            				new SortField(CREATED, SortField.Type.STRING, true)
    				));
            System.out.println("Hits: "+myHits.size());
            debug(results);
            
            String[] mustBe = new String[] {
            		"/db/test/test1.xml",
            		"/db/test/test3.xml",
            		"/db/test/test2.xml",
            		"/db/test/test4.xml",
            		"/db/test/test6.xml",
            		"/db/test/test5.xml",
            		};
            
            for (int index = 0; index < myHits.size(); index++) {
        		assertEquals("at index "+index, mustBe[index], myHits.get(index).getURI().toString());
            }

            cb.reset();
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }
    
    @Test
    public void testSymbolized() {
        System.out.println("Test symbolized ...");
        
        configureAndStore(COLLECTION_CONFIG7, 
                new Resource[] {
                    new Resource("test1.xml", XML1, metas1),
                    new Resource("test2.xml", XML1, metas2),
                    new Resource("test3.xml", XML1, metas3),
                    new Resource("test4.xml", XML1, metas4),
                    new Resource("test5.xml", XML1, metas5),
                    new Resource("test6.xml", XML1, metas6),
                });
        
        final ArrayList<DocumentImpl> myHits = new ArrayList<DocumentImpl>();
        
        Counter<DocumentImpl> cb = new Counter<DocumentImpl>() {
            @Override
            public void found(AtomicReader reader, int docNum, DocumentImpl document, float score) {
                super.found(reader, docNum, document, score);
                myHits.add( document );
                //System.out.println("Found! uri (IN TEST QUERY): " + document.getURI().toASCIIString() + " " + score);
            }
            
            public void reset() {
                super.reset();
                
                myHits.clear();
            }
        };
        
        DBBroker broker = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
            FacetSearchParams fsp = new FacetSearchParams(
                new CountFacetRequest(new CategoryPath(STATUS), 100)
            );

            MutableDocumentSet docs = new DefaultDocumentSet(1031);
            broker.getCollection(XmldbURI.xmldbUriFor("/db")).allDocs(broker, docs, true);

            List<QName> qNames = new ArrayList<QName>();
            qNames = worker.getDefinedIndexes(qNames);


            // Parse the query with no default field:
            Analyzer analyzer = new StandardAnalyzer(LuceneIndex.LUCENE_VERSION_IN_USE);

            String[] fields = new String[qNames.size()];
            int i = 0;
            for (QName qName : qNames) {
                fields[i] = LuceneUtil.encodeQName(qName, db.getSymbols());
                i++;
            }
            MultiFieldQueryParser parser = new MultiFieldQueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, fields, analyzer);
            Query query = parser.parse("a*");


            // Sort by status & creation date:
            List<FacetResult> results = QueryDocuments.query(
                    worker, docs, query, fsp, cb, 
                    200, 
                    new org.apache.lucene.search.Sort(
                        new SortField(STATUS, SortField.Type.STRING, true),
                        new SortField(CREATED, SortField.Type.STRING, true)
                ));
            System.out.println("Hits: "+myHits.size());
            debug(results);
            
            String encoded = worker.index.getSymbolTable().getIdtoHexString("in review");
            
            assertNotNull(encoded);
            
            String decoded = worker.index.getSymbolTable().getSymbolFromHexString(encoded);
            
            assertEquals("in review", decoded);
            
//            String[] mustBe = new String[] {
//                "/db/test/test1.xml",
//                "/db/test/test3.xml",
//                "/db/test/test2.xml",
//                "/db/test/test4.xml",
//                "/db/test/test6.xml",
//                "/db/test/test5.xml",
//            };
//            
//            for (int index = 0; index < myHits.size(); index++) {
//                assertEquals("at index "+index, mustBe[index], myHits.get(index).getURI().toString());
//            }
//
//            cb.reset();
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }

    
    @Test
    public void testsPossibleNPE() {
        System.out.println("Test NPE ...");
        
        configureAndStore(COLLECTION_CONFIG5, 
                new Resource[] {
                    new Resource("test1.xml", XML1, metas1),
                    new Resource("test2.xml", XML1, metas2),
                    new Resource("test3.xml", XML1, metas3),
                    new Resource("test4.xml", XML1, metas4),
                    new Resource("test5.xml", XML1, metas5),
                });
        
        final ArrayList<DocumentImpl> myHits = new ArrayList<DocumentImpl>();
        
        Counter<DocumentImpl> cb = new Counter<DocumentImpl>() {
            @Override
            public void found(AtomicReader reader, int docNum, DocumentImpl document, float score) {
            	super.found(reader, docNum, document, score);
                myHits.add( document );
                //System.out.println("Found! uri (IN TEST QUERY): " + doc.getURI().toASCIIString() + " " + v);
            }
            
            public void reset() {
            	super.reset();
            	
            	myHits.clear();
            }
        };
        
        DBBroker broker = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
            FacetSearchParams fsp = new FacetSearchParams(
                    new CountFacetRequest(new CategoryPath(STATUS), 100)
            );

            MutableDocumentSet docs = new DefaultDocumentSet(1031);
            broker.getCollection(XmldbURI.xmldbUriFor("/db")).allDocs(broker, docs, true);

            List<QName> qNames = new ArrayList<QName>();
            qNames = worker.getDefinedIndexes(qNames);


            // Parse the query with no default field:
            Analyzer analyzer = new StandardAnalyzer(LuceneIndex.LUCENE_VERSION_IN_USE);

            String[] fields = new String[qNames.size()];
            int i = 0;
            for (QName qName : qNames) {
                fields[i] = LuceneUtil.encodeQName(qName, db.getSymbols());
                i++;
            }
            MultiFieldQueryParser parser = new MultiFieldQueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, fields, analyzer);
            Query query = parser.parse("a*");


            // WORKS:
            List<FacetResult> results = QueryDocuments.query(worker, docs, query, fsp, cb);
            System.out.println("Hits: "+myHits.size());
            debug(results);
            
            cb.reset();

            // Default sort (by relevance) :
            results = QueryDocuments.query(worker, docs, query, fsp, cb, 
            		200, 
            		new org.apache.lucene.search.Sort()
            		);
            
            System.out.println("Hits: "+myHits.size());
            debug(results);

            cb.reset();

            // Sort by status:
            results = QueryDocuments.query(worker, docs, query, fsp, cb, 
            		200, 
            		new org.apache.lucene.search.Sort(
            				new SortField(STATUS, SortField.Type.STRING, true)
    				));
            System.out.println("Hits: "+myHits.size());
            debug(results);

            cb.reset();
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }
    
    @Test
    public void configureByAPI() {
        System.out.println("Test configuring by API ...");
        
        DBBroker broker = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            final LuceneIndex index = (LuceneIndex) db.getIndexManager().getIndexById(LuceneIndex.ID);

//          <lucene>
//        	<text qname="article">
//    			<ignore qname="note"/>
//    			<inline qname="s"/>
//    		</text>
//    		<text qname="p">
//    			<ignore qname="note"/>
//    			<inline qname="s"/>
//    		</text>
//    		<text qname="head"/>
//    		<ignore qname="note1"/>
//    		<inline qname="s2"/>
//    		</lucene>
            
            //configuring
            LuceneConfig conf = index.defineConfig(root);
            
            LuceneConfigText text = new LuceneConfigText(conf);
            text.setQName(new QName("article"));
            text.addIgnoreNode(new QName("note"));
            text.addInlineNode(new QName("s"));
            conf.add(text);
            
            text = new LuceneConfigText(conf);
            text.setQName(new QName("p"));
            text.addIgnoreNode(new QName("note"));
            text.addInlineNode(new QName("s"));
            conf.add(text);

            text = new LuceneConfigText(conf);
            text.setQName(new QName("head"));
            conf.add(text);
            
            conf.addIgnoreNode(new QName("note1"));
            conf.addInlineNode(new QName("s2"));

            //store
            DocumentSet docs = configureAndStore(null, 
                    new Resource[] {
                        new Resource("test1.xml", XML1, metas1),
                        new Resource("test2.xml", XML1, metas2),
                    });

            //query
            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
            FacetSearchParams fsp = new FacetSearchParams(
                new CountFacetRequest(new CategoryPath(STATUS), 10)
            );
            
            Counter<DocumentImpl> cb = new Counter<DocumentImpl>();
            
            List<QName> qnames = new ArrayList<QName>();
            qnames.add(new QName("head", ""));
            List<FacetResult> results = QueryDocuments.query(worker, docs, qnames, "title", fsp, null, cb);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet(results);
            
            cb.reset();

            //Lucene query
            QName qname = new QName("head", "");
            
            String field = LuceneUtil.encodeQName(new QName("head", ""), db.getSymbols());
            
            Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);

            QueryParser parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

            //worker.setOptions(options, parser);

            Query query = parser.parse("title");
            
            
            results = QueryDocuments.query(worker, docs, query, fsp, cb);
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet(results);

            cb.reset();
            
            //check document filtering
            qnames = new ArrayList<QName>();
            qnames.add(new QName("p", ""));
            results = QueryDocuments.query(worker, docs, qnames, "paragraph", fsp, null, cb);
            
            for (FacetResult result : results) {
                System.out.println(result.toString());
            }
            
            assertEquals(2, cb.count);
            assertEquals(2, cb.total);
            
            checkFacet(results);
            
            cb.reset();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            db.release(broker);
        }
    }
    
    private void debug(List<FacetResult> results) {
        for (FacetResult result : results) {
            System.out.println(result);
        }
    }
}


/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *  $Id$
 */
package org.exist.indexing.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.BitVector;
import org.apache.lucene.util.Version;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.indexing.lucene.PlainTextHighlighter.Offset;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.md.Meta;
import org.exist.storage.md.MetaDataImpl;
import org.exist.storage.md.Metas;
import org.exist.xquery.XPathException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class PlugToLucene {
    
    public static final String FIELD_META_DOC_URI = "metaDocUri";

    MetaDataImpl metadata;
    
    LuceneIndex index;
    LuceneIndexWorker worker;
    
    public PlugToLucene(MetaDataImpl metadata) {
        this.metadata = metadata;
        
        DBBroker broker = getBroker();
            
        worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        
        try {
            java.lang.reflect.Field field = worker.getClass().getDeclaredField("index");
            field.setAccessible(true);
            index = (LuceneIndex) field.get(worker);
            
        } catch (Exception e) {
            throw new RuntimeException("Can't get LuceneIndex", e);
        }
    }
    
    private DBBroker getBroker() {
        BrokerPool db;
        try {
            db = BrokerPool.getInstance();
        } catch (Exception e) {
            throw new RuntimeException("Can't get BrokerPool", e);
        }

        return db.getActiveBroker();
    }
    
    
    private DocumentImpl getDocument(Metas metas) {
        //object
        String uuid = metas.getUUID();
        
        DBBroker broker = getBroker();
        Subject currentSubject = broker.getSubject();
        try {
            broker.setSubject(broker.getDatabase().getSecurityManager().getSystemSubject());
            return metadata.getDocument(uuid);
        } catch (Exception e) {
            throw new RuntimeException("Document '"+uuid+"' not found.", e);
        } finally {
            broker.setSubject(currentSubject);
        }
    }
    
    public void addMetas(Metas metas) {
        
        //update lucene record

        DocumentImpl doc = getDocument(metas);
        
        //make sure that index worker do not process different document
        DocumentImpl indexDoc = worker.getDocument();
        if (indexDoc != null && !checkPendingDoc()) {
            throw new RuntimeException("Index processing different document '"+indexDoc.getFileURI()+"' ['"+doc.getFileURI()+"].");
        }
        
        // Note: code order is important here,
        //worker.setDocument(doc, StreamListener.STORE);
        //worker.setMode(StreamListener.STORE);
        
        indexMetas(doc, metas);
        
        //write
        //worker.writeNonXML();
    }
    
    private void indexMetas(DocumentImpl doc, Metas metas) {
        
        // create Lucene document
        Document pendingDoc = new Document();
        
        // Set DocId
        NumericField fDocId = new NumericField(LuceneIndexWorker.FIELD_DOC_ID, Field.Store.YES, true);
        fDocId.setIntValue(doc.getDocId());             
        pendingDoc.add(fDocId);
        
        // For binary documents the doc path needs to be stored
        String uri = metas.getURI();
        Field fDocUri = new Field(FIELD_META_DOC_URI, uri, Field.Store.YES, Field.Index.NOT_ANALYZED);
        pendingDoc.add(fDocUri);
        
        StringBuilder sb = new StringBuilder();
        
        // Iterate over all found fields and write the data.
        for (Meta meta : metas.metas()) {
            Object value = meta.getValue();
            if (! (value instanceof String)) {
                //ignore non string values
                continue;
            }
            
            // Get field type configuration
//            FieldType fieldType = config == null ? null : config.getFieldType(field.getName());
//            
            Field.Store store = null;
//            if (fieldType != null)
//                store = fieldType.getStore();
//            if (store == null)
                store = Field.Store.YES;//field.getStore();
            
            // Get name from SOLR field
            String contentFieldName = meta.getKey();
            
            //Analyzer fieldAnalyzer = (fieldType == null) ? null : fieldType.getAnalyzer();
               
            // Extract (document) Boost factor
//            if (field.getBoost() > 0) {
//                pendingDoc.setBoost(field.getBoost());
//            } 
            
            // Actual field content ; Store flag can be set in solrField
            Field contentField = new Field(contentFieldName, value.toString(), store, Field.Index.ANALYZED, Field.TermVector.YES);
            
            // Set boost value from SOLR config
            //contentField.setBoost(field.getBoost());
            
            pendingDoc.add(contentField);
            
            sb.append(value.toString()).append(" ");
        }
        
        Field contentField = new Field("ALL_METAS", sb.toString(), Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.YES);
        
        // Set boost value from SOLR config
        //contentField.setBoost(field.getBoost());
        
        pendingDoc.add(contentField);

        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            
            // by default, Lucene only indexes the first 10,000 terms in a field
            writer.setMaxFieldLength(Integer.MAX_VALUE);
            
            writer.addDocument(pendingDoc);
        } catch (IOException e) {
            //LOG.warn("An exception was caught while indexing document: " + e.getMessage(), e);

        } finally {
            index.releaseWriter(writer);
        }
    }
    
    public void removeMetas(Metas metas) {
        
        DocumentImpl doc = getDocument(metas);
        
        //make sure that index worker do not process different document
        DocumentImpl indexDoc = worker.getDocument();
        if (indexDoc != null && !checkPendingDoc()) {
            throw new RuntimeException("Index processing different document '"+indexDoc.getFileURI()+"' ['"+doc.getFileURI()+"].");
        }
        
        // Note: code order is important here,
        //worker.setDocument(doc, StreamListener.STORE);
        //worker.setMode(StreamListener.STORE);
        
        removeMetas(doc, metas);
        
        //write
        //worker.writeNonXML();
    }

    
    private void removeMetas(DocumentImpl doc, Metas metas) {
        
        //update lucene record

        IndexWriter writer = null;
        try {
            writer = index.getWriter();
            String uri = metas.getURI();
            Term dt = new Term(FIELD_META_DOC_URI, uri);
            writer.deleteDocuments(dt);
        } catch (IOException e) {
            //LOG.warn("Error while removing lucene index: " + e.getMessage(), e);
        } finally {
            index.releaseWriter(writer);
        }
    }
    
    private boolean checkPendingDoc() {
        try {
            java.lang.reflect.Field field = worker.getClass().getDeclaredField("pendingDoc");
            field.setAccessible(true);
            return (field.get(worker) == null);
            
        } catch (Exception e) {
        }
        
        return false;
    }

    public NodeImpl search(String queryText, List<String> toBeMatchedURIs) throws XPathException {
        BrokerPool db = null;
        DBBroker broker = null;
        try {
            db = BrokerPool.getInstance();
            broker = db.get(null);
            
            Subject currentSubject = broker.getSubject();
            try {
                
                broker.setSubject(db.getSecurityManager().getSystemSubject());
            
//                LuceneIndexWorker index = (LuceneIndexWorker) broker
//                        .getIndexController().getWorkerByIndexId(LuceneIndex.ID);
                
                return search(toBeMatchedURIs, queryText);

            } finally {
                broker.setSubject(currentSubject);
            }

        } catch (EXistException e) {
            throw new XPathException(e);
        } finally {
            if (db != null)
                db.release(broker);
        }
    }
    
    private NodeImpl search(List<String> toBeMatchedURIs, String queryText) throws XPathException {
        
        NodeImpl report = null;
        
        IndexSearcher searcher = null;
        try {
            // Get index searcher
            searcher = index.getSearcher();
            
            // Get analyzer : to be retrieved from configuration
            Analyzer searchAnalyzer = new StandardAnalyzer(Version.LUCENE_29);

            // Setup query Version, default field, analyzer
            QueryParser parser = new QueryParser(Version.LUCENE_29, "", searchAnalyzer);
            Query query = parser.parse(queryText);
                       
            // extract all used fields from query
            String[] fields = LuceneUtil.extractFields(query, searcher.getIndexReader());

            // Setup collector for results
            LuceneHitCollector collector = new LuceneHitCollector();
            
            // Perform actual search
            searcher.search(query, collector);

            // Retrieve all documents that match the query
            List<ScoreDoc> results = collector.getDocsByScore();
            
            // reusable attributes
            AttributesImpl attribs = null;
            
            PlainTextHighlighter highlighter = new PlainTextHighlighter(query, searcher.getIndexReader());
            
            MemTreeBuilder builder = new MemTreeBuilder();
            builder.startDocument();
            
            // start root element
            int nodeNr = builder.startElement("", "results", "results", null);
            
            BitVector processed = new BitVector(searcher.maxDoc());
            // Process result documents
            for (ScoreDoc scoreDoc : results) {
                if (processed.get(scoreDoc.doc))
                    continue;
                processed.set(scoreDoc.doc);
                
                Document doc = searcher.doc(scoreDoc.doc);
                
                // Get URI field of document                
                String fDocUri = doc.get(FIELD_META_DOC_URI);
                
                // Get score
                float score = scoreDoc.score;
                
                // Check if document URI has a full match or if a
                // document is in a collection
                if(isDocumentMatch(fDocUri, toBeMatchedURIs)){
                    
                    // setup attributes
                    attribs = new AttributesImpl();
                    attribs.addAttribute("", "uri", "uri", "CDATA", fDocUri);
                    attribs.addAttribute("", "score", "score", "CDATA", ""+score);

                    // write element and attributes
                    builder.startElement("", "search", "search", attribs);
                    for (String field : fields) {
                        String[] fieldContent = doc.getValues(field);
                        attribs.clear();
                        attribs.addAttribute("", "name", "name", "CDATA", field);
                        for (String content : fieldContent) {
                            List<Offset> offsets = highlighter.getOffsets(content, searchAnalyzer);
                            if (offsets != null) {
                                builder.startElement("", "field", "field", attribs);
                                highlighter.highlight(content, offsets, builder);
                                builder.endElement();
                            }
                        }
                    }
                    builder.endElement();

                    // clean attributes
                    attribs.clear();
                }           
            }
            
            // finish root element
            builder.endElement();
            
            //System.out.println(builder.getDocument().toString());
            
            // TODO check
            report = ((org.exist.memtree.DocumentImpl) builder.getDocument()).getNode(nodeNr);


        } catch (Exception ex){
            ex.printStackTrace();
            //LOG.error(ex);
            throw new XPathException(ex);
        
        } finally {
            index.releaseSearcher(searcher);
        }
        
        return report;
    }
    
    public List<String> searchDocuments(String queryText, List<String> toBeMatchedURIs) throws XPathException {
        
        List<String> uris = new ArrayList<String>();
        
        IndexSearcher searcher = null;
        try {
            // Get index searcher
            searcher = index.getSearcher();
            
            // Get analyzer : to be retrieved from configuration
            Analyzer searchAnalyzer = new StandardAnalyzer(Version.LUCENE_29);

            // Setup query Version, default field, analyzer
            QueryParser parser = new QueryParser(Version.LUCENE_29, "", searchAnalyzer);
            Query query = parser.parse(queryText);
                       
            // Setup collector for results
            LuceneHitCollector collector = new LuceneHitCollector();
            
            // Perform actual search
            searcher.search(query, collector);

            // Retrieve all documents that match the query
            List<ScoreDoc> results = collector.getDocsByScore();
            
            BitVector processed = new BitVector(searcher.maxDoc());
            // Process result documents
            for (ScoreDoc scoreDoc : results) {
                if (processed.get(scoreDoc.doc))
                    continue;
                processed.set(scoreDoc.doc);
                
                Document doc = searcher.doc(scoreDoc.doc);
                
                // Get URI field of document                
                String fDocUri = doc.get(FIELD_META_DOC_URI);
                
                // Get score
                float score = scoreDoc.score;
                
                // Check if document URI has a full match or if a
                // document is in a collection
                if(isDocumentMatch(fDocUri, toBeMatchedURIs)){
                    uris.add(fDocUri);
                }
            }
            
        } catch (Exception ex){
            ex.printStackTrace();
            //LOG.error(ex);
            throw new XPathException(ex);
        
        } finally {
            index.releaseSearcher(searcher);
        }
        
        return uris;
    }
    
    private boolean isDocumentMatch(String docUri, List<String> toBeMatchedUris){
        
        if(docUri==null){
            //LOG.error("docUri is null.");
            return false;
        }
        
        if(toBeMatchedUris==null){
            //LOG.error("match is null.");
            return false;
        }
        
        for(String doc : toBeMatchedUris){
            if( docUri.startsWith(doc) ){
                return true;
            }       
        }
        return false;
    }
    
    private static class LuceneHitCollector extends Collector {

        private List<ScoreDoc> docs = new ArrayList<ScoreDoc>();
        private int docBase;
        private Scorer scorer;

        private LuceneHitCollector() {
            //Nothing special to do
        }

        public List<ScoreDoc> getDocs() {
            Collections.sort(docs, new Comparator<ScoreDoc>() {

                public int compare(ScoreDoc scoreDoc, ScoreDoc scoreDoc1) {
                    if (scoreDoc.doc == scoreDoc1.doc)
                        return 0;
                    else if (scoreDoc.doc < scoreDoc1.doc)
                        return -1;
                    return 1;
                }
            });
            return docs;
        }
        
        /**
         * Get matching lucene documents by descending score
         * @return
         */
        public List<ScoreDoc> getDocsByScore() {
            Collections.sort(docs, new Comparator<ScoreDoc>() {

                public int compare(ScoreDoc scoreDoc, ScoreDoc scoreDoc1) {
                    if (scoreDoc.score == scoreDoc1.score)
                        return 0;
                    else if (scoreDoc.score < scoreDoc1.score)
                        return 1;
                    return -1;
                }
            });
            return docs;
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
            this.scorer = scorer;
        }

        @Override
        public void setNextReader(IndexReader indexReader, int docBase) throws IOException {
            this.docBase = docBase;
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return false;
        }

        @Override
        public void collect(int doc) {
            try {
                float score = scorer.score();
                docs.add(new ScoreDoc(doc + docBase, score));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

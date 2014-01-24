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
import java.util.List;
import java.util.Properties;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.exist.Database;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndexWorker.LuceneMatch;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.util.ByteConversion;
import org.exist.xquery.TerminatedException;
import org.w3c.dom.Node;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class QueryNodes {

    public static List<FacetResult> query(LuceneIndexWorker worker, QName qname, int contextId, DocumentSet docs,
            Query query, FacetSearchParams searchParams,
            SearchCallback<NodeProxy> callback) 
                    throws IOException, ParseException, TerminatedException {

        final LuceneIndex index = worker.index;

        final Database db = index.getBrokerPool();

        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

            DocumentHitCollector collector = new DocumentHitCollector(db, worker, query, qname, contextId, docs, callback, searchParams, taxonomyReader);

            searcher.search(query, collector);
            
            return collector.getFacetResults();
        } finally {
            index.releaseSearcher(searcher);
        }
    }

    public static List<FacetResult> query(LuceneIndexWorker worker, DocumentSet docs,
            List<QName> qnames, int contextId, String queryStr, FacetSearchParams searchParams, Properties options,
            SearchCallback<NodeProxy> callback) throws IOException, ParseException,
            TerminatedException {

        qnames = worker.getDefinedIndexes(qnames);

        final LuceneIndex index = worker.index;

        final Database db = index.getBrokerPool();
        
        DBBroker broker = db.getActiveBroker();
        
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

            DocumentHitCollector collector = new DocumentHitCollector(db, worker, null, null, contextId, docs, callback, searchParams, taxonomyReader);

            for (QName qname : qnames) {

                String field = LuceneUtil.encodeQName(qname, db.getSymbols());

                Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);

                QueryParser parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

                worker.setOptions(options, parser);

                Query query = parser.parse(queryStr);
                
                collector.qname = qname;
                collector.query = query;

                searcher.search(query, collector);
            }
            
            return collector.getFacetResults();
        } finally {
            index.releaseSearcher(searcher);
        }
    }
    
    private static class DocumentHitCollector extends QueryFacetCollector {

        private BinaryDocValues nodeIdValues;

        private final byte[] buf = new byte[1024];
        
        private final Database db;
        private final LuceneIndexWorker worker;
        private Query query;

        private QName qname;
        private final int contextId;

        private final SearchCallback<NodeProxy> callback;

        private DocumentHitCollector(
                
                final Database db,
                final LuceneIndexWorker worker,
                final Query query,
                
                final QName qname,
                final int contextId,
                                
                final DocumentSet docs, 
                final SearchCallback<NodeProxy> callback,
                
                final FacetSearchParams searchParams, 

                final TaxonomyReader taxonomyReader) {
            
            super(docs, searchParams, taxonomyReader);
            
            this.db = db;
            this.worker = worker;
            this.query = query;
            
            this.qname = qname;
            this.contextId = contextId;
            
            this.callback = callback;
        }

        @Override
        public void setNextReader(AtomicReaderContext atomicReaderContext) throws IOException {
            super.setNextReader(atomicReaderContext);
            nodeIdValues = this.reader.getBinaryDocValues(LuceneUtil.FIELD_NODE_ID);
        }

        @Override
        public void collect(int doc) {
            try {
                float score = scorer.score();
                int docId = (int) this.docIdValues.get(doc);
                
                DocumentImpl storedDocument = docs.getDoc(docId);
                if (storedDocument == null)
                    return;
                
                if (!docbits.contains(docId)) {
                    docbits.add(storedDocument);
    
                    bits.set(doc);
                    if (totalHits >= scores.length) {
                        float[] newScores = new float[ArrayUtil.oversize(totalHits + 1, 4)];
                        System.arraycopy(scores, 0, newScores, 0, totalHits);
                        scores = newScores;
                    }
                    scores[totalHits] = score;
                    totalHits++;
                }

                // XXX: understand: check permissions here? No, it may slowdown, better to check final set
                
                BytesRef ref = new BytesRef(buf);
                this.nodeIdValues.get(doc, ref);
                int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
                NodeId nodeId = db.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
                //LOG.info("doc: " + docId + "; node: " + nodeId.toString() + "; units: " + units);

                NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);
                if (qname != null)
                    storedNode.setNodeType(qname.getNameType() == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);

                LuceneMatch match = worker. new LuceneMatch(contextId, nodeId, query);
                match.setScore(score);
                storedNode.addMatch(match);
                callback.found(storedNode, score);
                //resultSet.add(storedNode, sizeHint);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

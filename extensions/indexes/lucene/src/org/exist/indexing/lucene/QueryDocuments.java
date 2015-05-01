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

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.ArrayUtil;
import org.exist.Database;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class QueryDocuments {

    public static List<FacetResult> query(LuceneIndexWorker worker, DocumentSet docs,
            Query query, FacetSearchParams searchParams,
            SearchCallback<DocumentImpl> callback)
            throws IOException, ParseException, XPathException {

        final LuceneIndex index = worker.index;

        return index.withSearcher(searcher -> {
            final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

            DocumentHitCollector collector = new DocumentHitCollector(docs, callback, searchParams, taxonomyReader);

            searcher.search(query, collector);
            
            return collector.getFacetResults();
        });
    }

    public static List<FacetResult> query(LuceneIndexWorker worker, DocumentSet docs,
            List<QName> qnames, String queryStr, FacetSearchParams searchParams, Properties options,
            SearchCallback<DocumentImpl> callback) throws IOException, ParseException,
            XPathException {

        final LuceneIndex index = worker.index;

        final Database db = index.getBrokerPool();
        
        DBBroker broker = db.getActiveBroker();
        
        return index.withSearcher(searcher -> {
            final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

            DocumentHitCollector collector = new DocumentHitCollector(docs, callback, searchParams, taxonomyReader);
            final List<QName> definedIndexes = worker.getDefinedIndexes(qnames);
            for (QName qname : definedIndexes) {

                String field = LuceneUtil.encodeQName(qname, db.getSymbols());

                Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);

                QueryParser parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

                try {
                    worker.setOptions(options, parser);

                    Query query = parser.parse(queryStr);

                    searcher.search(query, collector);
                } catch (ParseException e) {
                    throw new XPathException("Syntax error in Lucene query string: " + e.getMessage(), e);
                }
            }
            
            return collector.getFacetResults();
        });
    }
    
    private static class DocumentHitCollector extends QueryFacetCollector {

        private final SearchCallback<DocumentImpl> callback;

        private DocumentHitCollector(
                final DocumentSet docs, 
                final SearchCallback<DocumentImpl> callback,
                
                final FacetSearchParams searchParams, 

                final TaxonomyReader taxonomyReader) {
            
            super(docs, searchParams, taxonomyReader);
            
            this.callback = callback;
        }

        @Override
        public void collect(int doc) {
            try {
                float score = scorer.score();
                int docId = (int) this.docIdValues.get(doc);
                
                if (docbits.contains(docId)) {
                    return;
                }

                DocumentImpl storedDocument = docs.getDoc(docId);
                if (storedDocument == null)
                    return;
                
                docbits.add(storedDocument);

                bits.set(doc);
                if (totalHits >= scores.length) {
                    float[] newScores = new float[ArrayUtil.oversize(
                            totalHits + 1, 4)];
                    System.arraycopy(scores, 0, newScores, 0, totalHits);
                    scores = newScores;
                }
                scores[totalHits] = score;
                totalHits++;

                // XXX: understand: check permissions here? No, it may slowdown, better to check final set

                callback.found(storedDocument, score);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

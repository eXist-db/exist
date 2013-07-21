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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.encoding.DGapVInt8IntDecoder;
import org.apache.lucene.facet.params.CategoryListParams;
import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.params.CategoryListParams.OrdinalPolicy;
import org.apache.lucene.facet.search.CountingFacetsAggregator;
import org.apache.lucene.facet.search.FacetArrays;
import org.apache.lucene.facet.search.FacetRequest;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.search.FacetResultNode;
import org.apache.lucene.facet.search.FacetResultsHandler;
import org.apache.lucene.facet.search.FacetsAggregator;
import org.apache.lucene.facet.search.FastCountingFacetsAggregator;
import org.apache.lucene.facet.search.FloatFacetResultsHandler;
import org.apache.lucene.facet.search.IntFacetResultsHandler;
import org.apache.lucene.facet.search.TopKFacetResultsHandler;
import org.apache.lucene.facet.search.TopKInEachNodeHandler;
import org.apache.lucene.facet.search.FacetRequest.FacetArraysSource;
import org.apache.lucene.facet.search.FacetRequest.ResultMode;
import org.apache.lucene.facet.search.FacetRequest.SortOrder;
import org.apache.lucene.facet.search.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.taxonomy.ParallelTaxonomyArrays;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.util.PartitionsUtils;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.FixedBitSet;
import org.exist.Database;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.TerminatedException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class QueryDocuments {

    public interface SearchCallback {
        public void found(DocumentImpl document, float score);
    }
    
    public static List<FacetResult> query(LuceneIndexWorker worker, DocumentSet docs,
            Query query, FacetSearchParams searchParams,
            SearchCallback callback) 
                    throws IOException, ParseException, TerminatedException {

        final LuceneIndex index = worker.index;

        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

            DocumentHitCollector collector = new DocumentHitCollector(docs, callback, searchParams, taxonomyReader);

            searcher.search(query, collector);
            
            return collector.getFacetResults();
        } finally {
            index.releaseSearcher(searcher);
        }
    }

    public static List<FacetResult> query(LuceneIndexWorker worker, DocumentSet docs,
            List<QName> qnames, String queryStr, FacetSearchParams searchParams, Properties options,
            SearchCallback callback) throws IOException, ParseException,
            TerminatedException {

        qnames = worker.getDefinedIndexes(qnames);

        final LuceneIndex index = worker.index;

        final Database db = index.getBrokerPool();
        
        DBBroker broker = db.getActiveBroker();
        
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

            DocumentHitCollector collector = new DocumentHitCollector(docs, callback, searchParams, taxonomyReader);

            for (QName qname : qnames) {

                String field = LuceneUtil.encodeQName(qname, db.getSymbols());

                Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);

                QueryParser parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

                worker.setOptions(options, parser);

                Query query = parser.parse(queryStr);

                searcher.search(query, collector);
            }
            
            return collector.getFacetResults();
        } finally {
            index.releaseSearcher(searcher);
        }
    }
    
    private static class DocumentHitCollector extends Collector {

        private Scorer scorer;

        private AtomicReaderContext context;
        private AtomicReader reader;
        private NumericDocValues docIdValues;

        private final DocumentSet docs;
        private final SearchCallback callback;

        protected final List<MatchingDocs> matchingDocs = new ArrayList<MatchingDocs>();
        protected final FacetArrays facetArrays;
        
        protected final TaxonomyReader taxonomyReader;
        protected final FacetSearchParams searchParams;
    
        private int totalHits;
        private FixedBitSet bits;
        private float[] scores;

        private DefaultDocumentSet docbits;
        //private FixedBitSet docbits;

        private DocumentHitCollector(
                final DocumentSet docs, 
                final SearchCallback callback,
                
                final FacetSearchParams searchParams, 

                final TaxonomyReader taxonomyReader) {
            
            this.docs = docs;
            this.callback = callback;
            
            this.searchParams = searchParams;
            this.taxonomyReader = taxonomyReader;
            
//            this.facetArrays = new FacetArrays(taxonomyReader.getSize());
            
            this.facetArrays = new FacetArrays(
                    PartitionsUtils.partitionSize(searchParams.indexingParams, taxonomyReader));
            
            docbits = new DefaultDocumentSet(1031);//docs.getDocumentCount());
            //docbits = new FixedBitSet(docs.getDocumentCount());

        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
            this.scorer = scorer;
        }

        @Override
        public void setNextReader(AtomicReaderContext atomicReaderContext)
                throws IOException {
            reader = atomicReaderContext.reader();
            docIdValues = reader.getNumericDocValues(LuceneUtil.FIELD_DOC_ID);

            if (bits != null) {
                matchingDocs.add(new MatchingDocs(context, bits, totalHits, scores));
            }
            bits = new FixedBitSet(reader.maxDoc());
            totalHits = 0;
            scores = new float[64]; // some initial size
            context = atomicReaderContext;
        }
        
        protected void finish() {
          if (bits != null) {
            matchingDocs.add(new MatchingDocs(this.context, bits, totalHits, scores));
            bits = null;
            scores = null;
            context = null;
          }
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return false;
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

        private boolean verifySearchParams(FacetSearchParams fsp) {
            // verify that all category lists were encoded with DGapVInt
            for (FacetRequest fr : fsp.facetRequests) {
                CategoryListParams clp = fsp.indexingParams.getCategoryListParams(fr.categoryPath);
                if (clp.createEncoder().createMatchingDecoder().getClass() != DGapVInt8IntDecoder.class) {
                    return false;
                }
            }
    
            return true;
        }
    
        private FacetsAggregator getAggregator() {
            if (verifySearchParams(searchParams)) {
                return new FastCountingFacetsAggregator();
            } else {
                return new CountingFacetsAggregator();
            }
        }
    
        private Set<CategoryListParams> getCategoryLists() {
            if (searchParams.indexingParams.getAllCategoryListParams().size() == 1) {
                return Collections.singleton(searchParams.indexingParams.getCategoryListParams(null));
            }
    
            HashSet<CategoryListParams> clps = new HashSet<CategoryListParams>();
            for (FacetRequest fr : searchParams.facetRequests) {
                clps.add(searchParams.indexingParams.getCategoryListParams(fr.categoryPath));
            }
            return clps;
        }
    
        private FacetResultsHandler createFacetResultsHandler(FacetRequest fr) {
            if (fr.getDepth() == 1 && fr.getSortOrder() == SortOrder.DESCENDING) {
                FacetArraysSource fas = fr.getFacetArraysSource();
                if (fas == FacetArraysSource.INT) {
                    return new IntFacetResultsHandler(taxonomyReader, fr, facetArrays);
                }
    
                if (fas == FacetArraysSource.FLOAT) {
                    return new FloatFacetResultsHandler(taxonomyReader, fr, facetArrays);
                }
            }
    
            if (fr.getResultMode() == ResultMode.PER_NODE_IN_TREE) {
                return new TopKInEachNodeHandler(taxonomyReader, fr, facetArrays);
            }
            return new TopKFacetResultsHandler(taxonomyReader, fr, facetArrays);
        }
    
        private static FacetResult emptyResult(int ordinal, FacetRequest fr) {
            FacetResultNode root = new FacetResultNode(ordinal, 0);
            root.label = fr.categoryPath;
            return new FacetResult(fr, root, 0);
        }
        
        List<FacetResult> facetResults = null;
        
        public List<FacetResult> getFacetResults() throws IOException {
            if (facetResults == null) {
                finish();
                facetResults = accumulate();
            }
            return facetResults;
        }
    
        private List<FacetResult> accumulate() throws IOException {
            
            // aggregate facets per category list (usually only one category list)
            FacetsAggregator aggregator = getAggregator();
            for (CategoryListParams clp : getCategoryLists()) {
                for (MatchingDocs md : matchingDocs) {
                    aggregator.aggregate(md, clp, facetArrays);
                }
            }
    
            ParallelTaxonomyArrays arrays = taxonomyReader.getParallelTaxonomyArrays();
    
            // compute top-K
            final int[] children = arrays.children();
            final int[] siblings = arrays.siblings();
            List<FacetResult> res = new ArrayList<FacetResult>();
            for (FacetRequest fr : searchParams.facetRequests) {
                int rootOrd = taxonomyReader.getOrdinal(fr.categoryPath);
                // category does not exist
                if (rootOrd == TaxonomyReader.INVALID_ORDINAL) {
                    // Add empty FacetResult
                    res.add(emptyResult(rootOrd, fr));
                    continue;
                }
                CategoryListParams clp = searchParams.indexingParams.getCategoryListParams(fr.categoryPath);
                // someone might ask to aggregate ROOT category
                if (fr.categoryPath.length > 0) { 
                    OrdinalPolicy ordinalPolicy = clp.getOrdinalPolicy(fr.categoryPath.components[0]);
                    if (ordinalPolicy == OrdinalPolicy.NO_PARENTS) {
                        // rollup values
                        aggregator.rollupValues(fr, rootOrd, children, siblings, facetArrays);
                    }
                }
    
                FacetResultsHandler frh = createFacetResultsHandler(fr);
                res.add(frh.compute());
            }
            return res;
        }
    }
}

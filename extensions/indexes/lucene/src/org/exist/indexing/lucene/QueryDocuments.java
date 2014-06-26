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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldValueHitQueue;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.FieldValueHitQueue.Entry;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.PriorityQueue;
import org.exist.Database;
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
	
    public static Facets query(
    		LuceneIndexWorker worker, DocumentSet docs,
            Query query, FacetsConfig facetsConfig,
            SearchCallback<DocumentImpl> callback, int maxHits, Sort sort) 
                    throws IOException, ParseException, TerminatedException {

        final LuceneIndex index = worker.index;
        
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();
            
            FieldValueHitQueue<MyEntry> queue = FieldValueHitQueue.create(sort.getSort(), maxHits);

            ComparatorCollector collector = new ComparatorCollector(queue, maxHits, docs, callback);
            
            searcher.search(query, collector);
            
            //collector.context = searcher.getTopReaderContext();
            
            AtomicReader atomicReader = SlowCompositeReaderWrapper.wrap(searcher.getIndexReader());
            collector.context = atomicReader.getContext();
            
            //collector.finish();

            return collector.facets(index.getTaxonomyReader(), facetsConfig);

        } finally {
            index.releaseSearcher(searcher);
        }
    }

    public static Facets query(
            LuceneIndexWorker worker,
            DocumentSet docs,
            Query query,
            FacetsConfig facetsConfig,
            SearchCallback<DocumentImpl> callback
    ) throws IOException, ParseException, TerminatedException {
        
        return query(worker, docs, query, facetsConfig, callback, -1);
    }
    
    public static Facets query(
            LuceneIndexWorker worker,
            DocumentSet docs,
            Query query,
            FacetsConfig facetsConfig,
            SearchCallback<DocumentImpl> callback,
            int maxHits
    ) throws IOException, ParseException, TerminatedException {

        final LuceneIndex index = worker.index;

        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();

            DocumentHitCollector collector = new DocumentHitCollector(maxHits, docs, callback);

            searcher.search(query, collector);
            
            return collector.facets(index.getTaxonomyReader(), facetsConfig);

        } finally {
            index.releaseSearcher(searcher);
        }
    }

    public static Facets query(LuceneIndexWorker worker, DocumentSet docs,
            List<QName> qnames, String queryStr, FacetsConfig facetsConfig, Properties options,
            SearchCallback<DocumentImpl> callback) throws IOException, ParseException,
            TerminatedException {

        qnames = worker.getDefinedIndexes(qnames);

        final LuceneIndex index = worker.index;

        final Database db = index.getDatabase();
        
        DBBroker broker = db.getActiveBroker();
        
        IndexSearcher searcher = null;
        try {
            searcher = index.getSearcher();

            DocumentHitCollector collector = new DocumentHitCollector(-1, docs, callback);

            for (QName qname : qnames) {

                String field = LuceneUtil.encodeQName(qname, db.getSymbols());

                Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);

                QueryParser parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

                worker.setOptions(options, parser);

                Query query = parser.parse(queryStr);

                searcher.search(query, collector);
            }
            
            return collector.facets(index.getTaxonomyReader(), facetsConfig);

        } finally {
            index.releaseSearcher(searcher);
        }
    }
    
    private static class DocumentHitCollector extends QueryFacetCollector {
        
        protected final int numHits;

        protected final SearchCallback<DocumentImpl> callback;

        private DocumentHitCollector(
                final int numHits,
                final DocumentSet docs, 
                final SearchCallback<DocumentImpl> callback) {
            
            super(docs);
            
            this.numHits = numHits;
            this.callback = callback;
        }

        @Override
        public void collect(int doc) throws IOException {
            //in some cases can be null
            if (this.docIdValues == null) return;

            if (numHits > 0 && totalHits > numHits) {
                return;
            }
			try {
				float score = scorer.score();

				int docId = (int) this.docIdValues.get(doc);
	            
	            if (docbits.contains(docId)) return;

	            final DocumentImpl storedDocument = docs.getDoc(docId);
	            if (storedDocument == null) return;
	            
	            docbits.add(storedDocument);

	            collect(doc, storedDocument, score);

                super.collect(doc);

			} catch (IOException e) {
				e.printStackTrace();
			}
        }

        public void collect(int doc, DocumentImpl storedDocument, float score) {

            totalHits++;

            callback.found(reader, doc, storedDocument, score);
        }

		@Override
		protected SearchCallback<DocumentImpl> getCallback() {
			return callback;
		}
    }
    
    private static class ComparatorCollector extends QueryFacetCollector {

        SearchCallback<DocumentImpl> callback;
    	
        FieldComparator<?>[] comparators;
        final int[] reverseMul;
        final FieldValueHitQueue<MyEntry> queue;

    	public ComparatorCollector(
			final FieldValueHitQueue<MyEntry> queue,
			final int numHits,

            final DocumentSet docs,
            final SearchCallback<DocumentImpl> callback) {

    		super(docs);

            this.callback = callback;

			this.queue = queue;
			comparators = queue.getComparators();
			reverseMul = queue.getReverseMul();

			this.numHits = numHits;
		}

    	protected void finish() {

            callback.totalHits(queue.size());

            int size = queue.size();

            MyEntry[] array = new MyEntry[size];

            for (int i = size - 1; i >= 0; i--) {
                array[i] = queue.pop();
            }

            MyEntry entry = null;
            for (int i = 0; i < size; i++) {
                entry = array[i];
                collect(entry.doc, entry.document, entry.score);
            }

    		//super.finish();
    	}

		final void updateBottom(int doc, float score, DocumentImpl document) {
			// bottom.score is already set to Float.NaN in add().
			bottom.doc = docNumber(docBase, doc);
			bottom.score = score;
			bottom.document = document;
			bottom.context = context;
			bottom = queue.updateTop();
		}
		
	      @Override
	      public void collect(int doc) throws IOException {

              if (this.docIdValues == null) return;

          	int docId = (int) this.docIdValues.get(doc);
            if (docbits.contains(docId)) return;

            DocumentImpl storedDocument = docs.getDoc(docId);
            if (storedDocument == null) return;

            docbits.add(storedDocument);

            final float score = scorer.score();
//            System.out.println(score + " " + storedDocument.getDocumentURI());

            //check();

              super.collect( doc );


	        ++totalHits;
	        if (queueFull) {
	            if (comparators.length == 0) {
	                return;
	            }
	            // Fastmatch: return if this hit is not competitive
	            for (int i = 0;; i++) {
	              final int c = reverseMul[i] * comparators[i].compareBottom(doc);
	              if (c < 0) {
	                // Definitely not competitive.
	                return;
	              } else if (c > 0) {
	                // Definitely competitive.
	                break;
	              } else if (i == comparators.length - 1) {
	                // Here c=0. If we're at the last comparator, this doc is not
	                // competitive, since docs are visited in doc Id order, which means
	                // this doc cannot compete with any other document in the queue.
	                return;
	              }
	            }

	          // This hit is competitive - replace bottom element in queue & adjustTop
	          for (int i = 0; i < comparators.length; i++) {
	            comparators[i].copy(bottom.slot, doc);
	          }

	          updateBottom(doc, score, storedDocument);

	          for (int i = 0; i < comparators.length; i++) {
	            comparators[i].setBottom(bottom.slot);
	          }
	        } else {

	          // Startup transient: queue hasn't gathered numHits yet
	          final int slot = totalHits - 1;

	          // Copy hit into queue
	          for (int i = 0; i < comparators.length; i++) {
	            comparators[i].copy(slot, doc);
	          }

	          add(slot, doc, score, storedDocument);

	          if (queueFull) {
	            for (int i = 0; i < comparators.length; i++) {
	              comparators[i].setBottom(bottom.slot);
	            }
	          }
	        }
	      }

        public void collect(int doc, DocumentImpl storedDocument, float score) {

            totalHits++;

            callback.found(reader, doc, storedDocument, score);

        }
	    
	    @Override
	    public void setNextReader(AtomicReaderContext context) throws IOException {

	    	super.setNextReader(context);

	    	this.docBase = context.docBase;
	    	for (int i = 0; i < comparators.length; i++) {
	    		queue.setComparator(i, comparators[i].setNextReader(context));
	    	}
	    }
	    
	    @Override
	    public void setScorer(Scorer scorer) throws IOException {
	    	super.setScorer(scorer);

	        // set the scorer on all comparators
	        for (int i = 0; i < comparators.length; i++) {
	          comparators[i].setScorer(scorer);
	        }
	      }
	    
	    ///
	    final int numHits;
	    MyEntry bottom = null;
	    boolean queueFull;
	    int docBase;
	    
	    final void add(int slot, int doc, float score, DocumentImpl document) {
	        bottom = queue.add(new MyEntry(slot, docNumber(docBase, doc), score, document, context));
	        queueFull = totalHits == numHits;
	    }
	    
		private int docNumber(int docBase, int doc) {
			return docBase + doc;
		}
		
//		@Override
//		public boolean acceptsDocsOutOfOrder() {
//		    return true;
//		}

        @Override
        protected SearchCallback<DocumentImpl> getCallback() {
            return callback;
        }
    }
    
    private static class MyEntry extends Entry {
    	
    	AtomicReaderContext context;
    	
    	DocumentImpl document;
    	
    	public MyEntry(int slot, int doc, float score, DocumentImpl document, AtomicReaderContext context) {
    		super(slot, doc, score);
    		
    		this.context = context;
    		this.document = document;
		}
    	
    	@Override
    	public String toString() {
    		return super.toString() + " document " + document;
    	}
    }
}

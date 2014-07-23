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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.BinaryDocValues;
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
import org.apache.lucene.search.SortField;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
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

	public static Facets query(LuceneIndexWorker worker,
			QName qname, int contextId, 
			DocumentSet docs, Query query, FacetsConfig facetsConfig,
			SearchCallback<NodeProxy> callback, int maxHits, Sort sort)
			throws IOException, ParseException, TerminatedException {

		final LuceneIndex index = worker.index;
		
		final Database db = index.getDatabase();

		Set<String> fieldsToLoad = new HashSet<>();
		fieldsToLoad.add(LuceneUtil.FIELD_DOC_ID);

		IndexSearcher searcher = null;
		try {
			searcher = index.getSearcher();

            FieldValueHitQueue<MyEntry> queue;
            if (sort == null) {
                queue = FieldValueHitQueue.create(new SortField[0], maxHits);

            } else {
                queue = FieldValueHitQueue.create(sort.getSort(), maxHits);
            }
			

			ComparatorCollector collector = new ComparatorCollector(
					db, worker, query, qname, contextId, queue,
					maxHits, docs, callback);
			
			searcher.search(query, collector);

			// collector.context = searcher.getTopReaderContext();

			AtomicReader atomicReader = 
					SlowCompositeReaderWrapper.wrap(searcher.getIndexReader());
			collector.context = atomicReader.getContext();

			// collector.finish();

			return collector.facets(index.getTaxonomyReader(), facetsConfig);
		} finally {
			index.releaseSearcher(searcher);
		}
	}
        public static Facets query(LuceneIndexWorker worker,
                QName qname, int contextId, DocumentSet docs, Query query,
                FacetsConfig facetsConfig, SearchCallback<NodeProxy> callback)
                throws IOException, ParseException, TerminatedException {
            
            return query(worker, qname, contextId, docs, query, facetsConfig, callback, Integer.MAX_VALUE);
            
        }

	public static Facets query(LuceneIndexWorker worker,
			QName qname, int contextId, DocumentSet docs, Query query,
			FacetsConfig facetsConfig, SearchCallback<NodeProxy> callback, int maxHits)
			throws IOException, ParseException, TerminatedException {

		final LuceneIndex index = worker.index;

		final Database db = index.getDatabase();

		IndexSearcher searcher = null;
		try {
			searcher = index.getSearcher();

			NodeHitCollector collector = new NodeHitCollector(db, worker, maxHits, query, qname, contextId, docs, callback);

			searcher.search(query, collector);

			return collector.facets(index.getTaxonomyReader(), facetsConfig);

		} finally {
			index.releaseSearcher(searcher);
		}
	}

	public static Facets query(LuceneIndexWorker worker,
			DocumentSet docs, List<QName> qnames, int contextId,
			String queryStr, FacetsConfig facetsConfig,
			Properties options, SearchCallback<NodeProxy> callback)
			throws IOException, ParseException, TerminatedException {

		qnames = worker.getDefinedIndexes(qnames);

		final LuceneIndex index = worker.index;

		final Database db = index.getDatabase();

		DBBroker broker = db.getActiveBroker();

		IndexSearcher searcher = null;
		try {
			searcher = index.getSearcher();

			NodeHitCollector collector = 
					new NodeHitCollector(db, worker, -1, null, null, contextId, docs, callback);

			for (QName qname : qnames) {

				String field = LuceneUtil.encodeQName(qname, db.getSymbols());

				Analyzer analyzer = worker.getAnalyzer(null, qname, broker, docs);

				QueryParser parser = new QueryParser(
						LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

				worker.setOptions(options, parser);

				Query query = parser.parse(queryStr);

				collector.field = field;
				collector.qname = qname;
				collector.query = query;

				searcher.search(query, collector);
			}

			return collector.facets(index.getTaxonomyReader(), facetsConfig);

		} finally {
			index.releaseSearcher(searcher);
		}
	}

	private static class NodeHitCollector extends QueryFacetCollector {

		BinaryDocValues nodeIdValues;

		Database db;
		LuceneIndexWorker worker;
		
		int maxHits;
		Query query;

		String field = null;
		QName qname;
		int contextId;

		SearchCallback<NodeProxy> callback;

		private NodeHitCollector(

                Database db,
                LuceneIndexWorker worker,
		        
		        int maxHits,
				
				Query query,
	
				QName qname,
                int contextId,
		
				DocumentSet docs,
                SearchCallback<NodeProxy> callback
        ) {
	
				super(docs);

			this.db = db;
			this.worker = worker;
			
			this.maxHits = maxHits;
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
            //in some cases can be null
            if (this.docIdValues == null) return;

		    if (maxHits > 0 && totalHits > maxHits) {
		        return;
		    }

			try {
				float score = scorer.score();
				int docId = (int) this.docIdValues.get(doc);

				DocumentImpl storedDocument = docs.getDoc(docId);
				if (storedDocument == null)
					return;

				// XXX: understand: check permissions here? No, it may slowdown,
				// better to check final set

                NodeId nodeId;
                if (this.nodeIdValues == null) {
                    nodeId = NodeId.DOCUMENT_NODE;
                } else {
                    BytesRef ref = this.nodeIdValues.get(doc); //, ref);
                    int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
                    nodeId = db.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
                    // LOG.info("doc: " + docId + "; node: " + nodeId.toString() + "; units: " + units);
                }

				collect(doc, storedDocument, nodeId, score);

                super.collect( doc );

			} catch (Exception e) {
                LuceneIndex.LOG.error(e.getMessage(), e);
			}
		}

		public void collect(int doc, DocumentImpl storedDocument, NodeId nodeId, float score) {

			if (!docbits.contains(storedDocument.getDocId())) {
				docbits.add(storedDocument);

				totalHits++;
			}
            
			NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);
			if (qname != null)
				storedNode
					.setNodeType(
						qname.getNameType() == ElementValue.ATTRIBUTE ? 
							Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);


			LuceneMatch match = worker.new LuceneMatch(contextId, nodeId, query);
			match.setScore(score);
			//XXX: match.addOffset(offset, length);
			
			storedNode.addMatch(match);
			callback.found(reader, doc, storedNode, score);
			// resultSet.add(storedNode, sizeHint);
		}

		@Override
		protected SearchCallback<NodeProxy> getCallback() {
			return callback;
		}
	}

	private static class ComparatorCollector extends QueryFacetCollector {

        BinaryDocValues nodeIdValues;

        Database db;
        LuceneIndexWorker worker;
        Query query;

        String field = null;
        QName qname;
        int contextId;

		FieldComparator<?>[] comparators;
		int[] reverseMul;
		FieldValueHitQueue<MyEntry> queue;

        SearchCallback<NodeProxy> callback;

		public ComparatorCollector(
				Database db,
				LuceneIndexWorker worker,
				Query query,

				QName qname,
				int contextId,

				FieldValueHitQueue<MyEntry> queue,
				int numHits,

				DocumentSet docs,
				SearchCallback<NodeProxy> callback) {

			super(docs);

            this.db = db;

            this.qname = qname;
            this.worker = worker;
            this.query = query;

            this.contextId = contextId;

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
                collect(entry.doc, entry.document, entry.node, entry.score);
            }

			//super.finish();
		}

		final void updateBottom(int doc, float score, DocumentImpl document) {
			// bottom.score is already set to Float.NaN in add().
			bottom.doc = docBase + doc;
			bottom.score = score;
			bottom.document = document;
			bottom.node = getNodeId(doc);
			bottom.context = context;
			
			bottom = queue.updateTop();
		}

		@Override
		public void collect(int doc) {
		    if (this.docIdValues == null) return;

			try {
				final float score = scorer.score();

				int docId = (int) this.docIdValues.get(doc);

				DocumentImpl storedDocument = docs.getDoc(docId);
				if (storedDocument == null) return;

//				docbits.add(storedDocument);

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
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

        public void collect(int doc, DocumentImpl storedDocument, NodeId nodeId, float score) {

            if (!docbits.contains(storedDocument.getDocId())) {
                docbits.add(storedDocument);

                totalHits++;
            }

            NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);
            if (qname != null)
                storedNode
                        .setNodeType(
                                qname.getNameType() == ElementValue.ATTRIBUTE ?
                                        Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);


            LuceneMatch match = worker.new LuceneMatch(contextId, nodeId, query);
            match.setScore(score);
            //XXX: match.addOffset(offset, length);

            storedNode.addMatch(match);
            callback.found(reader, doc, storedNode, score);
            // resultSet.add(storedNode, sizeHint);
        }

		@Override
		public void setNextReader(AtomicReaderContext context) throws IOException {

			super.setNextReader(context);

            nodeIdValues = this.reader.getBinaryDocValues(LuceneUtil.FIELD_NODE_ID);

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

		// /
		final int numHits;
		MyEntry bottom = null;
		boolean queueFull;
		int docBase;

		final void add(int slot, int doc, float score, DocumentImpl document) {
			bottom = queue.add(
				new MyEntry(
					slot, 
					docBase + doc,
					score, 
					document, 
					getNodeId(doc), 
					context)
				);
			queueFull = totalHits == numHits;
		}
		
		private NodeId getNodeId(int doc) {
            if (this.nodeIdValues == null) {
                return NodeId.DOCUMENT_NODE;
            }

            BytesRef ref = this.nodeIdValues.get(doc);
			int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
			return db.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
		}

        @Override
        protected SearchCallback<NodeProxy> getCallback() {
            return callback;
        }
	}
	
	private static class MyEntry extends Entry {

		AtomicReaderContext context;

		DocumentImpl document;
		NodeId node;

		public MyEntry(int slot, int doc, float score, DocumentImpl document,
				NodeId node, AtomicReaderContext context) {
			super(slot, doc, score);
			
			this.context = context;

			this.document = document;
			this.node = node;
		}

		@Override
		public String toString() {
			return super.toString() + " document " + document + " node " + node;
		}
	}
}

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
import org.apache.lucene.facet.params.FacetSearchParams;
import org.apache.lucene.facet.search.FacetResult;
import org.apache.lucene.facet.search.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
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

	public static List<FacetResult> query(LuceneIndexWorker worker,
			QName qname, int contextId, 
			DocumentSet docs, Query query, FacetSearchParams searchParams,
			SearchCallback<NodeProxy> callback, int maxHits, Sort sort)
			throws IOException, ParseException, TerminatedException {

		final LuceneIndex index = worker.index;
		
		final Database db = index.getDatabase();

		Set<String> fieldsToLoad = new HashSet<String>();
		fieldsToLoad.add(LuceneUtil.FIELD_DOC_ID);

		IndexSearcher searcher = null;
		try {
			searcher = index.getSearcher();
			final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

                        FieldValueHitQueue<MyEntry> queue;
                        if (sort == null) {
                            queue = FieldValueHitQueue.create(new SortField[0], maxHits);
                            
                        } else {
                            queue = FieldValueHitQueue.create(sort.getSort(), maxHits);
                        }
			

			ComparatorCollector collector = new ComparatorCollector(
					db, worker, query, qname, contextId, queue,
					maxHits, docs, callback, searchParams, taxonomyReader);
			
			searcher.search(query, collector);

			// collector.context = searcher.getTopReaderContext();

			AtomicReader atomicReader = 
					SlowCompositeReaderWrapper.wrap(searcher.getIndexReader());
			collector.context = atomicReader.getContext();

			// collector.finish();

			return collector.getFacetResults();
		} finally {
			index.releaseSearcher(searcher);
		}
	}
        public static List<FacetResult> query(LuceneIndexWorker worker,
                QName qname, int contextId, DocumentSet docs, Query query,
                FacetSearchParams searchParams, SearchCallback<NodeProxy> callback)
                throws IOException, ParseException, TerminatedException {
            
            return query(worker, qname, contextId, docs, query, searchParams, callback, Integer.MAX_VALUE);
            
        }

	public static List<FacetResult> query(LuceneIndexWorker worker,
			QName qname, int contextId, DocumentSet docs, Query query,
			FacetSearchParams searchParams, SearchCallback<NodeProxy> callback, int maxHits)
			throws IOException, ParseException, TerminatedException {

		final LuceneIndex index = worker.index;

		final Database db = index.getDatabase();

		IndexSearcher searcher = null;
		try {
			searcher = index.getSearcher();
			final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

			NodeHitCollector collector = new NodeHitCollector(db,
					worker, maxHits, query, qname, contextId, docs, callback,
					searchParams, taxonomyReader);

			searcher.search(query, collector);

			return collector.getFacetResults();
		} finally {
			index.releaseSearcher(searcher);
		}
	}

	public static List<FacetResult> query(LuceneIndexWorker worker,
			DocumentSet docs, List<QName> qnames, int contextId,
			String queryStr, FacetSearchParams searchParams,
			Properties options, SearchCallback<NodeProxy> callback)
			throws IOException, ParseException, TerminatedException {

		qnames = worker.getDefinedIndexes(qnames);

		final LuceneIndex index = worker.index;

		final Database db = index.getDatabase();

		DBBroker broker = db.getActiveBroker();

		IndexSearcher searcher = null;
		try {
			searcher = index.getSearcher();
			final TaxonomyReader taxonomyReader = index.getTaxonomyReader();

			NodeHitCollector collector = 
					new NodeHitCollector(db, worker, -1, null, null, contextId, docs, callback,
					searchParams, taxonomyReader);

			for (QName qname : qnames) {

				String field = LuceneUtil.encodeQName(qname, db.getSymbols());

				Analyzer analyzer = worker.getAnalyzer(null, qname, broker,
						docs);

				QueryParser parser = new QueryParser(
						LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);

				worker.setOptions(options, parser);

				Query query = parser.parse(queryStr);

				collector.field = field;
				collector.qname = qname;
				collector.query = query;

				searcher.search(query, collector);
			}

			return collector.getFacetResults();
		} finally {
			index.releaseSearcher(searcher);
		}
	}

	private static class NodeHitCollector extends QueryFacetCollector {

		protected BinaryDocValues nodeIdValues;

		protected final byte[] buf = new byte[1024];

		protected final Database db;
		private final LuceneIndexWorker worker;
		
		private final int maxHits;
		private Query query;

		private String field = null;
		private QName qname;
		private final int contextId;

		protected final SearchCallback<NodeProxy> callback;

		private NodeHitCollector(final Database db, final LuceneIndexWorker worker, 
		        
		        final int maxHits,
				
				final Query query,
	
				final QName qname, final int contextId,
		
				final DocumentSet docs, final SearchCallback<NodeProxy> callback,
		
				final FacetSearchParams searchParams,
				final TaxonomyReader taxonomyReader) {
	
				super(docs, searchParams, taxonomyReader);

			this.db = db;
			this.worker = worker;
			
			this.maxHits = maxHits;
			this.query = query;

			this.qname = qname;
			this.contextId = contextId;

			this.callback = callback;
		}

		@Override
		public void setNextReader(AtomicReaderContext atomicReaderContext)
				throws IOException {
			super.setNextReader(atomicReaderContext);
			nodeIdValues = this.reader.getBinaryDocValues(LuceneUtil.FIELD_NODE_ID);
		}

		@Override
		public void collect(int doc) {
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

				BytesRef ref = new BytesRef(buf);
				this.nodeIdValues.get(doc, ref);
				int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
				NodeId nodeId = db.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
				// LOG.info("doc: " + docId + "; node: " + nodeId.toString() + "; units: " + units);

				collect(doc, storedDocument, nodeId, score);

			} catch (Exception e) {
                LuceneIndex.LOG.error(e.getMessage(), e);
			}
		}

		public void collect(int doc, DocumentImpl storedDocument, NodeId nodeId, float score) {

			if (!docbits.contains(storedDocument.getDocId())) {
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
            
			NodeProxy storedNode = new NodeProxy(storedDocument, nodeId);
			if (qname != null)
				storedNode
					.setNodeType(
						qname.getNameType() == ElementValue.ATTRIBUTE ? 
							Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);
			
			
//			if (field != null) {
//				try {
//					Terms termVector = reader.getTermVector(doc, field);
//					//Terms termVector = reader.terms(field);
//					if (termVector != null) {
//						if (termVector.hasOffsets()) {
//							TermsEnum term = termVector.iterator(null);
//							
//							BytesRef byteref;
//						    while ((byteref = term.next()) != null) {
//						    	
//						    	System.out.println(byteref.utf8ToString());
//						    	
//						        DocsAndPositionsEnum docPosEnum = term.docsAndPositions(null, null);//, DocsAndPositionsEnum.FLAG_OFFSETS);
//
//						        if (docPosEnum.advance(doc) != DocIdSetIterator.NO_MORE_DOCS) {
//							        int freq=docPosEnum.freq();
//							        for(int i=0; i<freq; i++){
//							            int position=docPosEnum.nextPosition();
//							            int start=docPosEnum.startOffset();
//							            int end=docPosEnum.endOffset();
//							            //Store start, end and position in an a list
//							            
//							            System.out.println(position+" = "+start+" : "+end);
//							        }
//						        }
//					        }
//						}
//					}
//					
//					System.out.println("=====================");

//					Terms terms = reader.terms(field);
//					TermsEnum termsEnum = terms.iterator(TermsEnum.EMPTY);
//					BytesRef term;
//				    while((term=termsEnum.next())!=null){
//				} catch (IOException e) {
//				}
//			}

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

	private static class ComparatorCollector extends NodeHitCollector {

		FieldComparator<?>[] comparators;
		final int[] reverseMul;
		final FieldValueHitQueue<MyEntry> queue;

		int maxDoc = 0;

		public ComparatorCollector(
				final Database db,
				final LuceneIndexWorker worker, 
				final Query query,

				final QName qname, 
				final int contextId,

				final FieldValueHitQueue<MyEntry> queue, 
				final int numHits,

				final DocumentSet docs,
				final SearchCallback<NodeProxy> callback,

				final FacetSearchParams searchParams,

				final TaxonomyReader taxonomyReader) {

			super(db, worker, -1, query, qname, contextId, docs, callback,
					searchParams, taxonomyReader);

			this.queue = queue;
			comparators = queue.getComparators();
			reverseMul = queue.getReverseMul();

			this.numHits = numHits;
		}

		@Override
		protected void finish() {
			if (bits != null) {
				if (context == null)
					throw new RuntimeException();

				matchingDocs.add(new MatchingDocs(context, bits, totalHits, scores));
			}
			bits = new FixedBitSet(maxDoc + 1);// 0x7FFFFFFF);//queue.size());
			totalHits = 0;
			scores = new float[64]; // some initial size

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
			
//		            Object[] array = LuceneUtil.getHeapArray(queue);
//		            
//		            for (int i = array.length - 1; i >= 0; i--) {
//		                MyEntry entry = (MyEntry) array[i];
//		                
//		                if (entry != null) {
//		                    collect(entry.doc, entry.document, entry.node, entry.score);
//		                }
//		            }
			
//			MyEntry entry;
//			while ((entry = queue.pop()) != null) {
//				collect(entry.doc, entry.document, entry.node, entry.score);
//			}

			super.finish();
		}

		final void updateBottom(int doc, float score, DocumentImpl document) {
			// bottom.score is already set to Float.NaN in add().
			bottom.doc = docNumber(docBase, doc);
			bottom.score = score;
			bottom.document = document;
			bottom.node = getNodeId(doc);
			bottom.context = context;
			
			bottom = queue.updateTop();
		}

		@Override
		public void collect(int doc) {
		    if (this.nodeIdValues == null) {
		        return;
		    }
			try {
				final float score = scorer.score();

				int docId = (int) this.docIdValues.get(doc);
//				if (docbits.contains(docId))
//					return;

				DocumentImpl storedDocument = docs.getDoc(docId);
				if (storedDocument == null)
					return;

//				docbits.add(storedDocument);

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

		// /
		final int numHits;
		MyEntry bottom = null;
		boolean queueFull;
		int docBase;

		final void add(int slot, int doc, float score, DocumentImpl document) {
			bottom = queue.add(
				new MyEntry(
					slot, 
					docNumber(docBase, doc), 
					score, 
					document, 
					getNodeId(doc), 
					context)
				);
			queueFull = totalHits == numHits;
		}
		
		private NodeId getNodeId(int doc) {
			BytesRef ref = new BytesRef(buf);

			this.nodeIdValues.get(doc, ref);
			int units = ByteConversion.byteToShort(ref.bytes, ref.offset);
			return db.getNodeFactory().createFromData(units, ref.bytes, ref.offset + 2);
		}
		
		private int docNumber(int docBase, int doc) {
			final int doca = docBase + doc;
			
			if (maxDoc < doca)
				maxDoc = doca;
			
			return doca;
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

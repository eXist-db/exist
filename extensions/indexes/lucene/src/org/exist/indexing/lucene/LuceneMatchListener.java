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

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.PhraseQuery;
import org.exist.dom.*;
import org.exist.indexing.AbstractMatchListener;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.util.serializer.AttrList;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.AttributeSource.State;

public class LuceneMatchListener extends AbstractMatchListener {

    private static final Logger LOG = Logger.getLogger(LuceneMatchListener.class);

    private Match match;

    private Map<Object, Query> termMap;

    private Map<NodeId, Offset> nodesWithMatch;

    private final LuceneIndex index;

    private LuceneConfig config;

    private DBBroker broker;

    public LuceneMatchListener(LuceneIndex index, DBBroker broker, NodeProxy proxy) {
        this.index = index;
        reset(broker, proxy);
    }

    public boolean hasMatches(NodeProxy proxy) {
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == LuceneIndex.ID) {
                return true;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        return false;
    }

    protected void reset(DBBroker broker, NodeProxy proxy) {
        this.broker = broker;
        this.match = proxy.getMatches();
        setNextInChain(null);

        IndexSpec indexConf = proxy.getDocument().getCollection().getIndexConfiguration(broker);
        if (indexConf != null)
            config = (LuceneConfig) indexConf.getCustomIndexSpec(LuceneIndex.ID);

        getTerms();
        nodesWithMatch = new TreeMap<>();
        /* Check if an index is defined on an ancestor of the current node.
        * If yes, scan the ancestor to get the offset of the first character
        * in the current node. For example, if the indexed node is &lt;a>abc&lt;b>de&lt;/b></a>
        * and we query for //a[text:ngram-contains(., 'de')]/b, proxy will be a &lt;b> node, but
        * the offsets of the matches are relative to the start of &lt;a>.
        */
        NodeSet ancestors = null;
        Match nextMatch = this.match;
        while (nextMatch != null) {
            if (proxy.getNodeId().isDescendantOf(nextMatch.getNodeId())) {
                if (ancestors == null)
                    ancestors = new NewArrayNodeSet();
                ancestors.add(new NodeProxy(proxy.getDocument(), nextMatch.getNodeId()));
            }
            nextMatch = nextMatch.getNextMatch();
        }

        if (ancestors != null && !ancestors.isEmpty()) {
            for (NodeProxy p : ancestors) {
                scanMatches(p);
            }
        }
    }

    @Override
    public void startElement(QName qname, AttrList attribs) throws SAXException {
        Match nextMatch = match;
        // check if there are any matches in the current element
        // if yes, push a NodeOffset object to the stack to track
        // the node contents
        while (nextMatch != null) {
            if (nextMatch.getNodeId().equals(getCurrentNode().getNodeId())) {
                scanMatches(new NodeProxy(getCurrentNode()));
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        super.startElement(qname, attribs);
    }

    @Override
    public void characters(CharSequence seq) throws SAXException {
        NodeId nodeId = getCurrentNode().getNodeId();
        Offset offset = nodesWithMatch.get(nodeId);
        if (offset == null)
            super.characters(seq);
        else {
            String s = seq.toString();
            int pos = 0;
            while (offset != null) {
                if (offset.startOffset > pos) {
                    if (offset.startOffset > seq.length())
                        throw new SAXException("start offset out of bounds");
                    super.characters(s.substring(pos, offset.startOffset));
                }
                int end = offset.endOffset;
                if (end > s.length())
                    end = s.length();
                super.startElement(MATCH_ELEMENT, null);
                super.characters(s.substring(offset.startOffset, end));
                super.endElement(MATCH_ELEMENT);
                pos = end;
                offset = offset.next;
            }
            if (pos < seq.length())
                super.characters(s.substring(pos));
        }
    }

    private void scanMatches(NodeProxy p) {
        // Collect the text content of all descendants of p. 
	// Remember the start offsets of the text nodes for later use.
        NodePath path = getPath(p);
        LuceneConfigText idxConf = config.getConfig(path).next();
        
        TextExtractor extractor = new DefaultTextExtractor();
        extractor.configure(config, idxConf, 0);
        OffsetList offsets = new OffsetList();
        int level = 0;
        int textOffset = 0;
        try {
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(p, false);
            while (reader.hasNext()) {
                int ev = reader.next();
                switch (ev) {
                    case XMLStreamConstants.END_ELEMENT:
                        if (--level < 0) {
                            break;
                        }
                        textOffset += extractor.endElement(reader.getQName());
                        break;
                    case XMLStreamConstants.START_ELEMENT:
                        ++level;
                        textOffset += extractor.startElement(reader.getQName());
                        break;
                    case XMLStreamConstants.CHARACTERS:
                        NodeId nodeId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                        textOffset += extractor.beforeCharacters();
                        offsets.add(textOffset, nodeId);
                        textOffset += extractor.characters(reader.getXMLText());
                        break;
                }
            }
        } catch (IOException | XMLStreamException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        }
        
        // Retrieve the Analyzer for the NodeProxy that was used for
	// indexing and querying.
        Analyzer analyzer = idxConf.getAnalyzer();
        if (analyzer == null) {
	    // Otherwise use system default Lucene analyzer (from conf.xml)
	    // to tokenize the text and find matching query terms.
	    analyzer = index.getDefaultAnalyzer();
        }
        LOG.debug("Analyzer: " + analyzer + " for path: " + path);
        String str = extractor.getText().toString();
        //Token token;
        try (TokenStream tokenStream = analyzer.tokenStream(null, new StringReader(str))) {
            tokenStream.reset();
            MarkableTokenFilter stream = new MarkableTokenFilter(tokenStream);
            while (stream.incrementToken()) {
                String text = stream.getAttribute(CharTermAttribute.class).toString();
                Query query = termMap.get(text);
                if (query != null) {
                    // Phrase queries need to be handled differently to filter
                    // out wrong matches: only the phrase should be marked, not
		    // single words which may also occur elsewhere in the document
                    if (query instanceof PhraseQuery) {
                        PhraseQuery phraseQuery = (PhraseQuery) query;
                        Term[] terms = phraseQuery.getTerms();
                        if (text.equals(terms[0].text())) {
                            // Scan the following text and collect tokens to see
			    // if they are part of the phrase.
                            stream.mark();
                            int t = 1;
                            List<State> stateList = new ArrayList<>(terms.length);
                            stateList.add(stream.captureState());
                            
                            while (stream.incrementToken() && t < terms.length) {
                                text = stream.getAttribute(CharTermAttribute.class).toString();
                                if (text.equals(terms[t].text())) {
                                    stateList.add(stream.captureState());
                                    if (++t == terms.length) {
                                        break;
                                    }
                                } else {
				    // Don't reset the token stream since we will 
				    // miss matches. /ljo
                                    //stream.reset();
                                    break;
                                }
                            }
                            
                            if (stateList.size() == terms.length) {
                                // we indeed have a phrase match. record the offsets of its terms.
                                int lastIdx = -1;
                                for (int i = 0; i < terms.length; i++) {
                                    stream.restoreState(stateList.get(i));
                                    
                                    OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                                    int idx = offsets.getIndex(offsetAttr.startOffset());
                                    
                                    NodeId nodeId = offsets.ids[idx];
                                    Offset offset = nodesWithMatch.get(nodeId);
                                    if (offset != null)
                                        if (lastIdx == idx)
                                            offset.setEndOffset(offsetAttr.endOffset() - offsets.offsets[idx]);
                                        else
                                            offset.add(offsetAttr.startOffset() - offsets.offsets[idx],
						       offsetAttr.endOffset() - offsets.offsets[idx]);
                                    else
                                        nodesWithMatch.put(nodeId, new Offset(offsetAttr.startOffset() - offsets.offsets[idx],
									      offsetAttr.endOffset() - offsets.offsets[idx]));
                                    lastIdx = idx;
                                }
                            }
                        } // End of phrase handling
                    } else {
                        
                        OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                        int idx = offsets.getIndex(offsetAttr.startOffset());
                        NodeId nodeId = offsets.ids[idx];
                        Offset offset = nodesWithMatch.get(nodeId);
                        if (offset != null)
                            offset.add(offsetAttr.startOffset() - offsets.offsets[idx],
				       offsetAttr.endOffset() - offsets.offsets[idx]);
                        else {
                            nodesWithMatch.put(nodeId, new Offset(offsetAttr.startOffset() - offsets.offsets[idx],
								  offsetAttr.endOffset() - offsets.offsets[idx]));
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        }
    }

    private NodePath getPath(NodeProxy proxy) {
        NodePath path = new NodePath();
        StoredNode node = (StoredNode) proxy.getNode();
        walkAncestor(node, path);
        return path;
    }

    private void walkAncestor(StoredNode node, NodePath path) {
        if (node == null)
            return;
        StoredNode parent = node.getParentStoredNode();
        walkAncestor(parent, path);
        path.addComponent(node.getQName());
    }

    /**
     * Get all query terms from the original queries.
     */
    private void getTerms() {
        Set<Query> queries = new HashSet<>();
        termMap = new TreeMap<>();
        Match nextMatch = this.match;
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == LuceneIndex.ID) {
                Query query = ((LuceneIndexWorker.LuceneMatch) nextMatch).getQuery();
                if (!queries.contains(query)) {
                    queries.add(query);
                    IndexReader reader = null;
                    try {
                        reader = index.getReader();
                        LuceneUtil.extractTerms(query, termMap, reader, false);
                    } catch (IOException | UnsupportedOperationException e) {
                        LOG.warn("Error while highlighting lucene query matches: " + e.getMessage(), e);
                    } finally {
                        index.releaseReader(reader);
                    }
                }
            }
            nextMatch = nextMatch.getNextMatch();
        }
    }

    private class OffsetList {

        int[] offsets = new int[16];
        NodeId[] ids = new NodeId[16];

        int len = 0;

        void add(int offset, NodeId nodeId) {
            if (len == offsets.length) {
                int[] tempOffsets = new int[len * 2];
                System.arraycopy(offsets, 0, tempOffsets, 0, len);
                offsets = tempOffsets;

                NodeId[] tempIds = new NodeId[len * 2];
                System.arraycopy(ids, 0, tempIds, 0, len);
                ids = tempIds;
            }
            offsets[len] = offset;
            ids[len++] = nodeId;
        }

        int getIndex(int offset) {
            for (int i = 0; i < len; i++) {
                if (offsets[i] <= offset && (i + 1 == len || offsets[i + 1] > offset)) {
                    return i;
                }
            }
            return -1;
        }

    }

    private class Offset {

        int startOffset;
        int endOffset;
        Offset next = null;

        Offset(int startOffset, int endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        void add(int offset, int endOffset) {
            if (startOffset == offset)
                // duplicate match starts at same offset. ignore.
                return;
            getLast().next = new Offset(offset, endOffset);
        }

        private Offset getLast() {
            Offset next = this;
            while (next.next != null) {
                next = next.next;
            }
            return next;
        }

        void setEndOffset(int offset) {
            getLast().endOffset = offset;
        }
    }
}

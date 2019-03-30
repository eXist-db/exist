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

import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.QName;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.PhraseQuery;
import org.exist.indexing.AbstractMatchListener;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.NodePath2;
import org.exist.util.serializer.AttrList;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.AttributeSource.State;

public class LuceneMatchListener extends AbstractMatchListener {

    private static final Logger LOG = LogManager.getLogger(LuceneMatchListener.class);

    private Match match;
    private Map<Object, Query> termMap;
    private Map<NodeId, Offset> nodesWithMatch;
    private final LuceneIndex index;
    private LuceneConfig config;
    private DBBroker broker;

    public LuceneMatchListener(final LuceneIndex index, final DBBroker broker, final NodeProxy proxy) {
        this.index = index;
        reset(broker, proxy);
    }

    public boolean hasMatches(final NodeProxy proxy) {
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId().equals(LuceneIndex.ID)) {
                return true;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        return false;
    }

    protected void reset(final DBBroker broker, final NodeProxy proxy) {
        this.broker = broker;
        this.match = proxy.getMatches();
        setNextInChain(null);

        final IndexSpec indexConf = proxy.getOwnerDocument().getCollection().getIndexConfiguration(broker);
        if (indexConf != null) {
            config = (LuceneConfig) indexConf.getCustomIndexSpec(LuceneIndex.ID);
        }

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
                if (ancestors == null) {
                    ancestors = new NewArrayNodeSet();
                }
                ancestors.add(new NodeProxy(proxy.getOwnerDocument(), nextMatch.getNodeId()));
            }
            nextMatch = nextMatch.getNextMatch();
        }

        if (ancestors != null && !ancestors.isEmpty()) {
            for (final NodeProxy p : ancestors) {
                scanMatches(p);
            }
        }
    }

    @Override
    public void startElement(final QName qname, final AttrList attribs) throws SAXException {
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
    public void characters(final CharSequence seq) throws SAXException {
        final NodeId nodeId = getCurrentNode().getNodeId();
        Offset offset = nodesWithMatch.get(nodeId);
        if (offset == null) {
            super.characters(seq);
        } else {
            final String s = seq.toString();
            int pos = 0;
            while (offset != null) {
                if (offset.startOffset > pos) {
                    if (offset.startOffset > seq.length()) {
                        throw new SAXException("start offset out of bounds");
                    }
                    super.characters(s.substring(pos, offset.startOffset));
                }
                int end = offset.endOffset;
                if (end > s.length()) {
                    end = s.length();
                }
                super.startElement(MATCH_ELEMENT, null);
                super.characters(s.substring(offset.startOffset, end));
                super.endElement(MATCH_ELEMENT);
                pos = end;
                offset = offset.next;
            }
            if (pos < seq.length()) {
                super.characters(s.substring(pos));
            }
        }
    }

    private void scanMatches(final NodeProxy p) {
        // Collect the text content of all descendants of p. 
        // Remember the start offsets of the text nodes for later use.
        final NodePath path = getPath(p);
        final LuceneIndexConfig idxConf = config.getConfig(path).next();

        final TextExtractor extractor = new DefaultTextExtractor();
        extractor.configure(config, idxConf);

        final OffsetList offsets = new OffsetList();
        int level = 0;
        int textOffset = 0;
        try {
            final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(p, false);
            while (reader.hasNext()) {
                final int ev = reader.next();
                switch (ev) {

                    case XMLStreamConstants.END_ELEMENT:
                        if (--level < 0) {
                            break;
                        }
                        // call extractor.endElement unless this is the root of the current fragment
                        if (level > 0) {
                            textOffset += extractor.endElement(reader.getQName());
                        }
                        break;

                    case XMLStreamConstants.START_ELEMENT:
                        // call extractor.startElement unless this is the root of the current fragment
                        if (level > 0) {
                            textOffset += extractor.startElement(reader.getQName());
                        }
                        ++level;
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        final NodeId nodeId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                        textOffset += extractor.beforeCharacters();
                        offsets.add(textOffset, nodeId);
                        textOffset += extractor.characters(reader.getXMLText());
                        break;
                }
            }
        } catch (final IOException | XMLStreamException e) {
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

        if (LOG.isDebugEnabled()) {
            LOG.debug("Analyzer: " + analyzer + " for path: " + path);
        }

        final String str = extractor.getText().toString();
        try (final Reader reader = new StringReader(str);
                final TokenStream tokenStream = analyzer.tokenStream(null, reader)) {
            tokenStream.reset();
            final MarkableTokenFilter stream = new MarkableTokenFilter(tokenStream);
            while (stream.incrementToken()) {
                String text = stream.getAttribute(CharTermAttribute.class).toString();
                final Query query = termMap.get(text);
                if (query != null) {
                    // Phrase queries need to be handled differently to filter
                    // out wrong matches: only the phrase should be marked, not
                    // single words which may also occur elsewhere in the document
                    if (query instanceof PhraseQuery) {
                        final PhraseQuery phraseQuery = (PhraseQuery) query;
                        final Term[] terms = phraseQuery.getTerms();
                        if (text.equals(terms[0].text())) {
                            // Scan the following text and collect tokens to see
                            // if they are part of the phrase.
                            stream.mark();
                            int t = 1;
                            final List<State> stateList = new ArrayList<>(terms.length);
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

                                    final OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                                    final int idx = offsets.getIndex(offsetAttr.startOffset());

                                    final NodeId nodeId = offsets.ids[idx];
                                    final Offset offset = nodesWithMatch.get(nodeId);
                                    if (offset != null) {
                                        if (lastIdx == idx) {
                                            offset.setEndOffset(offsetAttr.endOffset() - offsets.offsets[idx]);
                                        } else {
                                            offset.add(offsetAttr.startOffset() - offsets.offsets[idx],
                                                    offsetAttr.endOffset() - offsets.offsets[idx]);
                                        }
                                    } else {
                                        nodesWithMatch.put(nodeId, new Offset(offsetAttr.startOffset() - offsets.offsets[idx],
                                                offsetAttr.endOffset() - offsets.offsets[idx]));
                                    }

                                    lastIdx = idx;
                                }
                            }
                        } // End of phrase handling
                    } else {

                        final OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                        final int idx = offsets.getIndex(offsetAttr.startOffset());
                        final NodeId nodeId = offsets.ids[idx];
                        final Offset offset = nodesWithMatch.get(nodeId);
                        if (offset != null) {
                            offset.add(offsetAttr.startOffset() - offsets.offsets[idx],
                                    offsetAttr.endOffset() - offsets.offsets[idx]);
                        } else {
                            nodesWithMatch.put(nodeId, new Offset(offsetAttr.startOffset() - offsets.offsets[idx],
                                    offsetAttr.endOffset() - offsets.offsets[idx]));
                        }
                    }
                }
            }
        } catch (final IOException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        }
    }

    private NodePath getPath(final NodeProxy proxy) {
        final NodePath2 path = new NodePath2();
        final IStoredNode<?> node = (IStoredNode<?>) proxy.getNode();
        walkAncestor(node, path);
        return path;
    }

    private void walkAncestor(final IStoredNode node, final NodePath2 path) {
        if (node == null) {
            return;
        }
        final IStoredNode parent = node.getParentStoredNode();
        walkAncestor(parent, path);
        path.addNode(node);
    }

    /**
     * Get all query terms from the original queries.
     */
    private void getTerms() {
        try {
            index.withReader(reader -> {
                final Set<Query> queries = new HashSet<>();
                termMap = new TreeMap<>();
                Match nextMatch = this.match;
                while (nextMatch != null) {
                    if (nextMatch.getIndexId().equals(LuceneIndex.ID)) {
                        final Query query = ((LuceneMatch) nextMatch).getQuery();
                        if (!queries.contains(query)) {
                            queries.add(query);
                            LuceneUtil.extractTerms(query, termMap, reader, false);
                        }
                    }
                    nextMatch = nextMatch.getNextMatch();
                }
                return null;
            });
        } catch (final IOException e) {
            LOG.warn("Match listener caught IO exception while reading query tersm: " + e.getMessage(), e);
        }
    }

    private static class OffsetList {

        int[] offsets = new int[16];
        NodeId[] ids = new NodeId[16];

        int len = 0;

        void add(final int offset, final NodeId nodeId) {
            if (len == offsets.length) {
                final int[] tempOffsets = new int[len * 2];
                System.arraycopy(offsets, 0, tempOffsets, 0, len);
                offsets = tempOffsets;

                final NodeId[] tempIds = new NodeId[len * 2];
                System.arraycopy(ids, 0, tempIds, 0, len);
                ids = tempIds;
            }
            offsets[len] = offset;
            ids[len++] = nodeId;
        }

        int getIndex(final int offset) {
            for (int i = 0; i < len; i++) {
                if (offsets[i] <= offset && (i + 1 == len || offsets[i + 1] > offset)) {
                    return i;
                }
            }
            return -1;
        }

    }

    private static class Offset {
        private final int startOffset;
        private int endOffset;
        private Offset next = null;

        Offset(final int startOffset, final int endOffset) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        void add(final int offset, final int endOffset) {
            if (startOffset == offset) {
                // duplicate match starts at same offset. ignore.
                return;
            }
            getLast().next = new Offset(offset, endOffset);
        }

        private Offset getLast() {
            Offset next = this;
            while (next.next != null) {
                next = next.next;
            }
            return next;
        }

        void setEndOffset(final int offset) {
            getLast().endOffset = offset;
        }
    }
}

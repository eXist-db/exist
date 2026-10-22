/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.QName;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
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

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.*;


public class LuceneMatchListener extends AbstractMatchListener {

    private static final Logger LOG = LogManager.getLogger(LuceneMatchListener.class);

    private Match match;
    /** where the queries of the node's matches match a text */
    private MatchLocator locator;
    /** the fields the queries search that a node's own text is indexed in: not the configured fields */
    private Set<String> textFields;
    private Map<NodeId, Offset> nodesWithMatch;
    private final LuceneIndex index;
    private LuceneConfig config;
    private DBBroker broker;
    /** NodeId we already scanned in reset(); avoid double-scan in startElement. */
    private NodeId scannedInResetForNodeId;

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
        } else {
            config = LuceneConfig.DEFAULT_CONFIG;
        }

        getQueries();
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
                ancestors.add(new NodeProxy(proxy.getExpression(), proxy.getOwnerDocument(), nextMatch.getNodeId()));
            }
            nextMatch = nextMatch.getNextMatch();
        }

        scannedInResetForNodeId = null;
        /* #5738: scanMatches is the per-node hot path. When no query searches the text's own
         * fields (e.g., a query of configured fields only, excluded since PR #3467, the
         * typical case for `lemma:Aachen` style field queries), nothing in the text can
         * match, so nodesWithMatch will stay empty and characters() will pass through
         * unchanged. Skip the scan entirely in that case. */
        if (textFields.isEmpty()) {
            return;
        }
        if (ancestors != null && !ancestors.isEmpty()) {
            for (final NodeProxy p : ancestors) {
                scanMatches(p);
            }
        } else {
            /* #4835: When proxy is the matching node (no ancestors), scan it directly.
             * Otherwise nodesWithMatch stays empty until startElement, but when serializing
             * multiple nodes the listener may be reused with stale state from a previous node. */
            Match m = this.match;
            while (m != null) {
                if (m.getNodeId().equals(proxy.getNodeId())) {
                    scanMatches(proxy);
                    scannedInResetForNodeId = proxy.getNodeId();
                    break;
                }
                m = m.getNextMatch();
            }
        }
    }

    @Override
    public void startElement(final QName qname, final AttrList attribs) throws SAXException {
        /* #5738: when no query searches the text's own fields nothing can match, so
         * deep-scanning child elements to discover match offsets is wasted work; just pass
         * the event through. */
        if (textFields == null || textFields.isEmpty()) {
            super.startElement(qname, attribs);
            return;
        }
        Match nextMatch = match;
        final NodeHandle current = getCurrentNode();
        // check if there are any matches in the current element
        // if yes, push a NodeOffset object to the stack to track
        // the node contents
        while (nextMatch != null && current != null) {
            if (nextMatch.getNodeId().equals(current.getNodeId())) {
                if (scannedInResetForNodeId == null || !scannedInResetForNodeId.equals(current.getNodeId())) {
                    scanMatches(new NodeProxy(null, current));
                }
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        super.startElement(qname, attribs);
    }

    @Override
    public void characters(final CharSequence seq) throws SAXException {
        final NodeHandle current = getCurrentNode();
        if (current == null) {
            super.characters(seq);
            return;
        }
        final NodeId nodeId = current.getNodeId();
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
        @Nullable final LuceneIndexConfig idxConf = config.getConfig(path).next();
        if(idxConf == null) {
            return;  // there is no index config so there can not be any matches
        }
        final TextExtractor extractor = new DefaultTextExtractor();
        extractor.configure(config, idxConf);

        final OffsetList offsets = new OffsetList();
        int level = 0;
        int textOffset = 0;
        try {
            final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(p, false);
            scanLoop:
            while (reader.hasNext()) {
                final int ev = reader.next();
                switch (ev) {

                    case XMLStreamConstants.END_ELEMENT:
                        if (--level < 0) {
                            break scanLoop;
                        }
                        // call extractor.endElement unless this is the root of the current fragment
                        if (level > 0) {
                            textOffset += extractor.endElement(reader.getQName());
                        }
                        /* #4835: Stop when we've closed the root element we're scanning.
                         * The reader continues to siblings; without this we'd include the whole parent. */
                        if (level == 0) {
                            break scanLoop;
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
                        final int consumed = extractor.characters(reader.getXMLText());
                        if (consumed > 0) {
                            offsets.add(textOffset, nodeId);
                            textOffset += consumed;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (final IOException | XMLStreamException e) {
            LOG.warn("Problem found while serializing XML: {}", e.getMessage(), e);
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
            LOG.debug("Analyzer: {} for path: {}", analyzer, path);
        }

        final String str = extractor.getText().toString();
        // the element's own field, if a query searches it; with a qname, the text is indexed in that field only
        final Set<String> fields;
        if (idxConf.getQName() != null) {
            final String field = LuceneUtil.encodeQName(idxConf.getQName(), index.getBrokerPool().getSymbols());
            fields = textFields.contains(field) ? Set.of(field) : textFields;
        } else {
            fields = textFields;
        }
        try {
            for (final MatchLocator.Span span : locator.locate(fields, str, analyzer)) {
                addMatchSpan(span.start(), span.end(), offsets, str.length());
            }
        } catch (final IOException e) {
            LOG.warn("Problem found while serializing XML: {}", e.getMessage(), e);
        }
    }

    public static NodePath getPath(final NodeProxy proxy) {
        final NodePath2 path = new NodePath2();
        final IStoredNode<?> node = (IStoredNode<?>) proxy.getNode();
        walkAncestor(node, path);
        return path;
    }

    private static void walkAncestor(final IStoredNode node, final NodePath2 path) {
        if (node == null) {
            return;
        }
        final IStoredNode parent = node.getParentStoredNode();
        walkAncestor(parent, path);
        path.addNode(node);
    }

    /**
     * Get the queries of the node's Lucene matches, and the fields they search that a node's text is indexed in.
     * Configured Lucene fields (e.g. pub-year) are left out, so that util:expand does not produce superfluous
     * highlights for field-only matches.
     *
     * <p>A wildcard or prefix query is expanded against the text itself when its matches are located, so no
     * expansion against the index is kept from one node, or one query, to the next.
     *
     * @see <a href="https://github.com/eXist-db/exist/pull/3467">PR #3467</a>
     */
    private void getQueries() {
        final Set<Query> uniqueQueries = collectUniqueLuceneQueries();
        locator = new MatchLocator(uniqueQueries);
        final Set<String> excludedFields = (config == null || config == LuceneConfig.DEFAULT_CONFIG)
                ? Collections.emptySet()
                : config.getConfiguredFieldNames();
        final Set<String> fields = new HashSet<>(locator.fields());
        fields.removeAll(excludedFields);
        textFields = fields;
    }

    /**
     * Walk the match list and collect each unique Lucene query exactly once.
     */
    private Set<Query> collectUniqueLuceneQueries() {
        final Set<Query> uniqueQueries = new HashSet<>();
        Match nextMatch = this.match;
        while (nextMatch != null) {
            if (nextMatch.getIndexId().equals(LuceneIndex.ID)) {
                uniqueQueries.add(((LuceneMatch) nextMatch).getQuery());
            }
            nextMatch = nextMatch.getNextMatch();
        }
        return uniqueQueries;
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

        /**
         * End offset of segment idx in the concatenated string.
         * @param idx segment index (0-based)
         * @param textLength total length of concatenated text
         */
        int getSegmentEnd(final int idx, final int textLength) {
            return idx + 1 < len ? offsets[idx + 1] : textLength;
        }

    }

    /**
     * Add a match span [startOffset, endOffset) to all text nodes it intersects.
     * Fixes #4584: when a Lucene hit spans inline elements (e.g. "ro&lt;vuji&gt;s&lt;/vuji&gt;e"),
     * all portions must get exist:match, not just the first text node.
     *
     * @param startOffset inclusive start in concatenated string
     * @param endOffset exclusive end in concatenated string
     * @param offsets offset list mapping positions to text nodes
     * @param textLength total length of concatenated text
     */
    private void addMatchSpan(final int startOffset, final int endOffset,
            final OffsetList offsets, final int textLength) {
        if (startOffset < 0 || endOffset <= startOffset) {
            return;
        }
        final int idxStart = offsets.getIndex(startOffset);
        final int idxEnd = offsets.getIndex(endOffset - 1);
        if (idxStart < 0 || idxEnd < 0) {
            return;
        }
        for (int idx = idxStart; idx <= idxEnd; idx++) {
            final NodeId nodeId = offsets.ids[idx];
            final int nodeStart = offsets.offsets[idx];
            final int nodeEnd = offsets.getSegmentEnd(idx, textLength);
            final int relStart = (idx == idxStart) ? startOffset - nodeStart : 0;
            final int relEnd = (idx == idxEnd) ? endOffset - nodeStart : nodeEnd - nodeStart;
            final Offset existing = nodesWithMatch.get(nodeId);
            if (existing != null) {
                existing.add(relStart, relEnd);
            } else {
                nodesWithMatch.put(nodeId, new Offset(relStart, relEnd));
            }
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

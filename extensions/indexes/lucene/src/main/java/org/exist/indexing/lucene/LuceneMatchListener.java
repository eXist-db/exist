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

import javax.annotation.Nullable;
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
    /** NodeId we already scanned in reset(); avoid double-scan in startElement. */
    private NodeId scannedInResetForNodeId;

    /* #5738: cache the rewritten terms per Query so that batch util:expand(...) does not
     * re-rewrite the same wildcard/prefix queries on every input node. The cache is keyed
     * by Query identity (Lucene Query.equals is content-based, so semantically equal
     * queries share an entry) and is bounded to avoid unbounded growth across long-lived
     * brokers. */
    private static final int QUERY_TERM_CACHE_MAX = 32;
    private final LinkedHashMap<Query, Map<Object, Query>> queryTermCache =
            new LinkedHashMap<>(QUERY_TERM_CACHE_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<Query, Map<Object, Query>> eldest) {
                    return size() > QUERY_TERM_CACHE_MAX;
                }
            };

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
                ancestors.add(new NodeProxy(proxy.getExpression(), proxy.getOwnerDocument(), nextMatch.getNodeId()));
            }
            nextMatch = nextMatch.getNextMatch();
        }

        scannedInResetForNodeId = null;
        /* #5738: scanMatches is the per-node hot path. When termMap is empty (e.g., every
         * query term was filtered out by the configured-fields exclusion from PR #3467,
         * the typical case for `lemma:Aachen` style field queries), no token in the entry
         * text can match, so nodesWithMatch will stay empty and characters() will pass
         * through unchanged. Skip the scan entirely in that case. */
        if (termMap.isEmpty()) {
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
        /* #5738: when termMap is empty no token can match, so deep-scanning child elements
         * to discover match offsets is wasted work; just pass the event through. */
        if (termMap == null || termMap.isEmpty()) {
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
        try (final Reader reader = new StringReader(str);
             final TokenStream tokenStream = analyzer.tokenStream(null, reader);
             final MarkableTokenFilter stream = new MarkableTokenFilter(tokenStream)) {
            stream.reset();
            while (stream.incrementToken()) {
                String text = stream.getAttribute(CharTermAttribute.class).toString();
                final Query query = termMap.get(text);
                if (query != null) {
                    // Phrase queries need to be handled differently to filter
                    // out wrong matches: only the phrase should be marked, not
                    // single words which may also occur elsewhere in the document
                    if (query instanceof PhraseQuery phraseQuery) {
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
                                // Phrase match: add one span from first to last term (may cross text nodes, #4584).
                                stream.restoreState(stateList.getFirst());
                                final int start = stream.getAttribute(OffsetAttribute.class).startOffset();
                                stream.restoreState(stateList.get(terms.length - 1));
                                final int end = stream.getAttribute(OffsetAttribute.class).endOffset();
                                addMatchSpan(start, end, offsets, str.length());
                            }
                        } // End of phrase handling
                    } else {
                        final OffsetAttribute offsetAttr = stream.getAttribute(OffsetAttribute.class);
                        addMatchSpan(offsetAttr.startOffset(), offsetAttr.endOffset(), offsets, str.length());
                    }
                }
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
     * Get all query terms from the original queries.
     * Excludes terms from configured Lucene fields (e.g. pub-year) so that
     * util:expand does not produce superfluous highlights for field-only matches.
     *
     * <p>For #5738: the per-Query cache lets batch util:expand($hits) reuse rewritten
     * terms across hits. Without this cache every reset() reopened the IndexReader and
     * re-enumerated terms (slow for wildcard/prefix queries on large corpora).
     *
     * @see <a href="https://github.com/eXist-db/exist/pull/3467">PR #3467</a>
     * @see <a href="https://github.com/eXist-db/exist/issues/5738">Issue #5738</a>
     */
    private void getTerms() {
        // Collect unique queries from the proxy's match list. The cache shortcut applies
        // when every query is already cached - the common case in batch util:expand calls.
        final Set<Query> uniqueQueries = collectUniqueLuceneQueries();
        if (uniqueQueries.isEmpty()) {
            termMap = Collections.emptyMap();
            return;
        }
        final Set<String> excludedFields = (config == null || config == LuceneConfig.DEFAULT_CONFIG)
                ? Collections.emptySet()
                : config.getConfiguredFieldNames();
        final List<Query> uncachedQueries = new ArrayList<>();
        for (final Query q : uniqueQueries) {
            if (!queryTermCache.containsKey(q)) {
                uncachedQueries.add(q);
            }
        }
        if (!uncachedQueries.isEmpty()) {
            try {
                index.withReader(reader -> {
                    for (final Query q : uncachedQueries) {
                        final Map<Object, Query> rawTerms = new HashMap<>();
                        LuceneUtil.extractTerms(q, rawTerms, reader, true);
                        queryTermCache.put(q, rawTerms);
                    }
                    return null;
                });
            } catch (final IOException e) {
                LOG.warn("Match listener caught IO exception while reading query terms: {}", e.getMessage(), e);
                termMap = Collections.emptyMap();
                return;
            }
        }
        termMap = buildTermMap(uniqueQueries, excludedFields);
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

    /**
     * Combine per-query cached rawTerms into a single termText -> Query map, applying the
     * configured-field exclusion at the end so different listener instances with different
     * configs share the same cached rawTerms.
     */
    private Map<Object, Query> buildTermMap(final Set<Query> queries, final Set<String> excludedFields) {
        final Map<Object, Query> result = new TreeMap<>();
        for (final Query q : queries) {
            final Map<Object, Query> rawTerms = queryTermCache.get(q);
            if (rawTerms == null) {
                // Race against eviction is impossible here (single-threaded reset()), but be
                // defensive in case the cache size becomes 0 in some future revision.
                continue;
            }
            for (final Map.Entry<Object, Query> e : rawTerms.entrySet()) {
                if (e.getKey() instanceof Term term && !excludedFields.contains(term.field())) {
                    result.put(term.text(), e.getValue());
                }
            }
        }
        return result;
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

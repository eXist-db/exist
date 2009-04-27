/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.exist.dom.Match;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.AbstractMatchListener;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.util.serializer.AttrList;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class LuceneMatchListener extends AbstractMatchListener {

    private static final Logger LOG = Logger.getLogger(LuceneMatchListener.class);

    private Match match;

    private Set termSet;

    private Map nodesWithMatch;

    private LuceneIndex index;

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
        nodesWithMatch = new TreeMap();
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
            for (Iterator i = ancestors.iterator(); i.hasNext();) {
                NodeProxy p = (NodeProxy) i.next();
                scanMatches(p);
            }
        }
    }

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

    public void characters(CharSequence seq) throws SAXException {
        NodeId nodeId = getCurrentNode().getNodeId();
        Offset offset = (Offset) nodesWithMatch.get(nodeId);
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
        // Collect the text content of all descendants of p. Remember the start offsets
        // of the text nodes for later use.
        TextExtractor extractor = new DefaultTextExtractor();
        extractor.configure(config);
        OffsetList offsets = new OffsetList();
        int level = 0;
        int textOffset = 0;
        try {
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(p, false);
            while (reader.hasNext()) {
                int ev = reader.next();
                switch (ev) {
                    case XMLStreamReader.END_ELEMENT:
                        if (--level < 0)
                            break;
                        textOffset += extractor.endElement(reader.getQName());
                        break;
                    case XMLStreamReader.START_ELEMENT:
                        ++level;
                        textOffset += extractor.startElement(reader.getQName());
                        break;
                    case XMLStreamReader.CHARACTERS:
                        NodeId nodeId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
                        offsets.add(textOffset, nodeId);
                        textOffset += extractor.characters(reader.getXMLText());
                        break;
                }
            }
        } catch (IOException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        } catch (XMLStreamException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        }
        // Use Lucene's analyzer to tokenize the text and find matching query terms
        TokenStream stream = index.getDefaultAnalyzer().tokenStream(null, new StringReader(extractor.getText().toString()));
        Token token;
        try {

            while ((token = stream.next()) != null) {
                String text = token.termText();
                if (termSet.contains(text)) {
                    int idx = offsets.getIndex(token.startOffset());
                    NodeId nodeId = offsets.ids[idx];
                    Offset offset = (Offset) nodesWithMatch.get(nodeId);
                    if (offset != null)
                        offset.add(token.startOffset() - offsets.offsets[idx],
                                token.endOffset() - offsets.offsets[idx]);
                    else
                        nodesWithMatch.put(nodeId, new Offset(token.startOffset() - offsets.offsets[idx],
                                token.endOffset() - offsets.offsets[idx]));
                }
            }
        } catch (IOException e) {
            LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
        }
    }

    /**
     * Get all query terms from the original queries.
     */
    private void getTerms() {
        Set queries = new HashSet();
        Set set = new TreeSet();
        Match nextMatch = this.match;
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == LuceneIndex.ID) {
                Query query = ((LuceneIndexWorker.LuceneMatch) nextMatch).getQuery();
                if (!queries.contains(query)) {
                    queries.add(query);
                    IndexReader reader = null;
                    try {
                        reader = index.getReader();
                        query = query.rewrite(reader);
                    } catch (IOException e) {
                        LOG.warn("Error while highlighting lucene query matches: " + e.getMessage(), e);
                    } finally {
                        index.releaseReader(reader);
                    }
                    query.extractTerms(set);
                }
            }
            nextMatch = nextMatch.getNextMatch();
        }
        termSet = new TreeSet();
        for (Iterator i = set.iterator(); i.hasNext(); ) {
            Term term = (Term) i.next();
            termSet.add(term.text());
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
            Offset next = this;
            while (next.next != null) {
                next = next.next;
            }
            next.next = new Offset(offset, endOffset);
        }
    }
}

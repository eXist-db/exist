package org.exist.fulltext;

import org.apache.log4j.Logger;
import org.exist.dom.*;
import org.exist.fulltext.FTIndexWorker;
import org.exist.indexing.AbstractMatchListener;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.util.FastQSort;
import org.exist.util.serializer.AttrList;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * Implementation of {@link org.exist.indexing.MatchListener} for the fulltext index.
 * Right now, the serializer will directly plug this into the listener pipeline. This will
 * change once we move the fulltext index into its own module.
 */
public class FTMatchListener extends AbstractMatchListener {

    private final static Logger LOG = Logger.getLogger(FTMatchListener.class);

    private Match match;
    private Stack offsetStack = null;

    public FTMatchListener() {
    }

    public FTMatchListener(NodeProxy proxy) {
        reset(proxy);
    }

    public boolean hasMatches(NodeProxy proxy) {
        Match nextMatch = proxy.getMatches();
        while (nextMatch != null) {
            if (nextMatch.getIndexId() == FTIndex.ID) {
                return true;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        return false;
    }

    public void reset(NodeProxy proxy) {
        this.match = proxy.getMatches();
        setNextInChain(null);

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
                    ancestors = new ExtArrayNodeSet();
                ancestors.add(new NodeProxy(proxy.getDocument(), nextMatch.getNodeId()));
            }
            nextMatch = nextMatch.getNextMatch();
        }
        if (ancestors != null && !ancestors.isEmpty()) {
            for (Iterator i = ancestors.iterator(); i.hasNext();) {
                NodeProxy p = (NodeProxy) i.next();
                int startOffset = 0;
                try {
                    XMLStreamReader reader = proxy.getDocument().getBroker().getXMLStreamReader(p, false);
                    while (reader.hasNext()) {
                        int ev = reader.next();
                        NodeId nodeId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
                        if (nodeId.equals(proxy.getNodeId()))
                            break;
                        if (ev == XMLStreamReader.CHARACTERS)
                            startOffset += reader.getText().length();
                    }
                } catch (IOException e) {
                    LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
                } catch (XMLStreamException e) {
                    LOG.warn("Problem found while serializing XML: " + e.getMessage(), e);
                }
                if (offsetStack == null)
                    offsetStack = new Stack();
                offsetStack.push(new NodeOffset(p.getNodeId(), startOffset));
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
                if (offsetStack == null)
                    offsetStack = new Stack();
                offsetStack.push(new NodeOffset(nextMatch.getNodeId()));
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        super.startElement(qname, attribs);
    }

    public void endElement(QName qname) throws SAXException {
        Match nextMatch = match;
        // check if we need to pop the stack
        while (nextMatch != null) {
            if (nextMatch.getNodeId().equals(getCurrentNode().getNodeId())) {
                offsetStack.pop();
                break;
            }
            nextMatch = nextMatch.getNextMatch();
        }
        super.endElement(qname);
    }

    public void characters(CharSequence seq) throws SAXException {
        List offsets = null;    // a list of offsets to process
        if (offsetStack != null) {
            // walk through the stack to find matches which start in
            // the current string of text
            for (int i = 0; i < offsetStack.size(); i++) {
                NodeOffset no = (NodeOffset) offsetStack.get(i);
                int end = no.offset + seq.length();
                // scan all matches
                Match next = match;
                while (next != null) {
                    if (next.getIndexId() == FTIndex.ID && next.getNodeId().equals(no.nodeId)) {
                        int freq = next.getFrequency();
                        for (int j = 0; j < freq; j++) {
                            Match.Offset offset = next.getOffset(j);
                            if (offset.getOffset() < end &&
                                offset.getOffset() + offset.getLength() > no.offset) {
                                // add it to the list to be processed
                                if (offsets == null) {
                                    offsets = new ArrayList(4);
                                }
                                // adjust the offset and add it to the list
                                int start = offset.getOffset() - no.offset;
                                int len = offset.getLength();
                                if (start < 0) {
                                    len = len - Math.abs(start);
                                    start = 0;
                                }
                                if (start + len > seq.length())
                                    len = seq.length() - start;
                                offsets.add(new Match.Offset(start, len));
                            }
                        }
                    }
                    next = next.getNextMatch();
                }
                // add the length of the current text to the element content length
                no.offset = end;
            }
        }
        // walk through the matches a second time to find matches in the text node itself
        Match next = match;
        while (next != null) {
            if (next.getIndexId() == FTIndex.ID &&
                next.getNodeId().equals(getCurrentNode().getNodeId())) {
                if (offsets == null)
                    offsets = new ArrayList();
                int freq = next.getFrequency();
                for (int i = 0; i < freq; i++) {
                    offsets.add(next.getOffset(i));
                }
            }
            next = next.getNextMatch();
        }
        // now print out the text, marking all matches with a match element
        if (offsets != null) {
            FastQSort.sort(offsets, 0, offsets.size() - 1);
            String s = seq.toString();
            int pos = 0;
            for (int i = 0; i < offsets.size(); i++) {
                Match.Offset offset = (Match.Offset) offsets.get(i);
                if (offset.getOffset() > pos) {
                    super.characters(s.substring(pos, pos + (offset.getOffset() - pos)));
                }
                super.startElement(MATCH_ELEMENT, null);
                super.characters(s.substring(offset.getOffset(), offset.getOffset() + offset.getLength()));
                super.endElement(MATCH_ELEMENT);
                pos = offset.getOffset() + offset.getLength();
            }
            if (pos < s.length()) {
                super.characters(s.substring(pos));
            }
        } else
            super.characters(seq);
    }

    private class NodeOffset {
        NodeId nodeId;
        int offset = 0;

        public NodeOffset(NodeId nodeId) {
            this.nodeId = nodeId;
        }

        public NodeOffset(NodeId nodeId, int offset) {
            this.nodeId = nodeId;
            this.offset = offset;
        }
    }
}

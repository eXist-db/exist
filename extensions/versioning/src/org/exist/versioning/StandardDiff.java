package org.exist.versioning;

import bmsi.util.Diff;
import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;

public class StandardDiff implements org.exist.versioning.Diff {

    private final static Logger LOG = Logger.getLogger(StandardDiff.class);

    public final static String NAMESPACE = "http://exist-db.org/versioning";
    public final static String PREFIX = "v";
    
    private final static QName DIFF_ELEMENT = new QName("diff", NAMESPACE, PREFIX);

    private DBBroker broker;

    private List changes = null;

    public StandardDiff(DBBroker broker) {
        this.broker = broker;
    }

    public void diff(DocumentImpl docA, DocumentImpl docB)
    throws DiffException {
        try {
            DiffNode[] nodesA = getNodes(broker, docA);
            DiffNode[] nodesB = getNodes(broker, docB);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Source:");
                debugNodes(nodesA);
                LOG.trace("Modified:");
                debugNodes(nodesB);
            }

            Diff diff = new Diff(nodesA, nodesB);
            Diff.change script = diff.diff_2(false);
            changes = getChanges(script, docA, docB, nodesA, nodesB);
        } catch (XMLStreamException e) {
            throw new DiffException(e.getMessage(), e);
        } catch (IOException e) {
            throw new DiffException(e.getMessage(), e);
        }
    }

    private void debugNodes(DiffNode[] nodes) {
        StringBuffer buf = new StringBuffer();
        buf.append('\n');
        for (int i = 0; i < nodes.length; i++) {
            DiffNode node = nodes[i];
            buf.append(Integer.toString(i)).append(' ').append(node.toString()).append('\n');
        }
        LOG.trace(buf.toString());
    }

    public String diff2XML() throws DiffException {
        try {
            StringWriter writer = new StringWriter();
            SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);
            Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            outputProperties.setProperty(OutputKeys.INDENT, "no");
            sax.setOutput(writer, outputProperties);
            sax.startDocument();
            diff2XML(sax);
            sax.endDocument();
            return writer.toString();
        } catch (SAXException e) {
            throw new DiffException("error while serializing diff: " + e.getMessage(), e);
        }
    }
    
    public void diff2XML(Receiver receiver ) throws DiffException {
        try {
            receiver.startElement(DIFF_ELEMENT, null);
            for (int i = 0; i < changes.size(); i++) {
                Difference diff = (Difference) changes.get(i);
                diff.serialize(broker, receiver);
            }
            receiver.endElement(DIFF_ELEMENT);
            receiver.endDocument();
        } catch (SAXException e) {
            throw new DiffException("error while serializing diff: " + e.getMessage(), e);
        }
    }


    protected List getChanges(Diff.change script, DocumentImpl docA, DocumentImpl docB, DiffNode[] nodesA, DiffNode[] nodesB) throws XMLStreamException {
        List changes = new ArrayList();
        Map inserts = new TreeMap();
        Diff.change next = script;
        while (next != null) {
            int start0 = next.line0;
            int start = next.line1;
            int last = start + next.inserted;
            int lastDeleted = start0 + next.deleted;

            if (next.inserted > 0) {
                if (next.deleted == 0) {
                    // Simplify edit script: if there's a set of start tags at the end of the
                    // insertion, check if they correspond to similar start tags *before* the
                    // inserted section. If yes, move the inserted range to match the entire
                    // inserted element instead of a sequence of end/start tags.
                    int offsetFix = 0;
                    for (int i = last - 1; i > start; i--) {
                        DiffNode node = nodesB[i];
                        if (node.nodeType == XMLStreamReader.START_ELEMENT && start - (last - i) > 0) {
                            DiffNode before = nodesB[start - (last - i)];
                            if (before.nodeType == XMLStreamReader.START_ELEMENT &&
                                before.qname.equals(node.qname))
                                offsetFix++;
                        } else
                            break;
                    }
                    if (offsetFix > 0) {
                        start = start - offsetFix;
                        start0 = start0 - offsetFix;
                        last = start + next.inserted;
                    }
                }
                Difference.Insert diff;
                if (nodesA[start0].nodeType == XMLStreamReader.END_ELEMENT) {
                    diff = new Difference.Append(new NodeProxy(docA, nodesA[start0].nodeId), docB);
                    changes.add(diff);
                } else {
                    diff = (Difference.Insert) inserts.get(nodesA[start0].nodeId);
                    if (diff == null) {
                        diff = new Difference.Insert(new NodeProxy(docA, nodesA[start0].nodeId), docB);
                        inserts.put(nodesA[start0].nodeId, diff);
                    }
                }
                
                // now scan the chunk and collect the nodes
                DiffNode[] nodes = new DiffNode[last - start];
                int j = 0;
                for (int i = start; i < last; i++, j++) {
                    if (LOG.isTraceEnabled())
                        LOG.trace(Integer.toString(i) + " " + nodesB[i]);
                    nodes[j] = nodesB[i];
                }
                diff.addNodes(nodes);
            }
            if (next.deleted > 0) {
                if (LOG.isTraceEnabled())
                    LOG.trace("Deleted: " + start0 + " last: " + lastDeleted);
                for (int i = start0; i < lastDeleted; i++) {
                    boolean elementDeleted = false;
                    if (nodesA[i].nodeType == XMLStreamReader.START_ELEMENT) {
                        for (int j = i; j < lastDeleted; j++) {
                            if (nodesA[j].nodeType == XMLStreamReader.END_ELEMENT &&
                                    nodesA[j].nodeId.equals(nodesA[i].nodeId)) {
                                Difference.Delete diff = new Difference.Delete(new NodeProxy(docA, nodesA[i].nodeId));
                                changes.add(diff);
                                i = j;
                                elementDeleted = true;
                                break;
                            }
                        }
                    }
                    if (!elementDeleted) {
                        Difference.Delete diff = new Difference.Delete(nodesA[i].nodeType, new NodeProxy(docA, nodesA[i].nodeId));
                        changes.add(diff);
                    }
                }
            }
            next = next.link;
        }
        for (Iterator i = inserts.values().iterator(); i.hasNext();) {
            changes.add(i.next());
        }
        return changes;
    }

    protected DiffNode[] getNodes(DBBroker broker, DocumentImpl root) throws XMLStreamException, IOException {
        EmbeddedXMLStreamReader reader = broker.newXMLStreamReader(new NodeProxy(root, NodeId.DOCUMENT_NODE, root.getFirstChildAddress()), false);
        List nodes = new ArrayList();
        DiffNode node;
        while (reader.hasNext()) {
            int status = reader.next();
            NodeId nodeId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
            switch (status) {
                case XMLStreamReader.START_ELEMENT:
                    node = new DiffNode(nodeId, status, reader.getQName());
                    nodes.add(node);

                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        nodeId = reader.getAttributeId(i);
                        String value = reader.getAttributeQName(i).getStringValue() + '=' +
                                reader.getAttributeValue(i);
                        node = new DiffNode(nodeId, XMLStreamReader.ATTRIBUTE, value);
                        nodes.add(node);
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
                    node = new DiffNode(nodeId, status, reader.getQName());
                    nodes.add(node);
                    break;
                case XMLStreamReader.CHARACTERS:
                case XMLStreamReader.COMMENT:
                    node = new DiffNode(nodeId, status, reader.getText());
                    nodes.add(node);
                    break;
                case XMLStreamReader.PROCESSING_INSTRUCTION:
                    String value = reader.getPITarget() + " " + reader.getPIData();
                    nodes.add(new DiffNode(nodeId, status, value));
                    break;
            }
        }
        DiffNode[] array = new DiffNode[nodes.size()];
        return (DiffNode[]) nodes.toArray(array);
    }

}
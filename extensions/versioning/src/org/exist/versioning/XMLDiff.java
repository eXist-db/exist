package org.exist.versioning;

import bmsi.util.Diff;
import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class XMLDiff {

    private final static Logger LOG = Logger.getLogger(XMLDiff.class);

    public final static String NAMESPACE = "http://exist-db.org/versioning";
    public final static String PREFIX = "v";
    
    private final static QName DIFF_ELEMENT = new QName("version", NAMESPACE, PREFIX);
    private final static QName PROPERTIES_ELEMENT = new QName("properties", NAMESPACE, PREFIX);

    private DBBroker broker;

    public XMLDiff(DBBroker broker) {
        this.broker = broker;
    }

    public String diff(DocumentImpl docA, DocumentImpl docB, Properties properties)
    throws DiffException {
        try {
            ElementImpl root = (ElementImpl) docA.getDocumentElement();
            DiffNode[] nodesA = getNodes(broker, docA);
            root = (ElementImpl) docB.getDocumentElement();
            DiffNode[] nodesB = getNodes(broker, docB);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Source:");
                debugNodes(nodesA);
                LOG.trace("Modified:");
                debugNodes(nodesB);
            }

            Diff diff = new Diff(nodesA, nodesB);
            Diff.change script = diff.diff_2(false);
            List changes = getChanges(script, docA, docB, nodesA, nodesB);
            return diff2XML(changes, properties);
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

    protected String diff2XML(List changes, Properties properties) throws DiffException {
        try {
            StringWriter writer = new StringWriter();
            SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);
            Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            outputProperties.setProperty(OutputKeys.INDENT, "no");
            sax.setOutput(writer, outputProperties);
            sax.startDocument();
            sax.startElement(DIFF_ELEMENT, null);
            writeProperties(sax, properties);
            for (int i = 0; i < changes.size(); i++) {
                Difference diff = (Difference) changes.get(i);
                diff.serialize(broker, sax);
            }
            sax.endElement(DIFF_ELEMENT);
            sax.endDocument();
            return writer.toString();
        } catch (SAXException e) {
            throw new DiffException("error while serializing diff: " + e.getMessage(), e);
        }
    }

    protected void writeProperties(Receiver receiver, Properties properties) throws SAXException {
        receiver.startElement(PROPERTIES_ELEMENT, null);
        for (Iterator i = properties.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            QName qn = new QName(key, NAMESPACE, PREFIX);
            receiver.startElement(qn, null);
            receiver.characters(properties.get(key).toString());
            receiver.endElement(qn);
        }
        receiver.endElement(PROPERTIES_ELEMENT);
    }

    protected List getChanges(Diff.change script, DocumentImpl docA, DocumentImpl docB, DiffNode[] nodesA, DiffNode[] nodesB) throws XMLStreamException {
        List changes = new ArrayList();
        Diff.change next = script;
        while (next != null) {
            int start0 = next.line0;
            int start = next.line1;
            int last = start + next.inserted;
            int lastDeleted = start0 + next.deleted;

            if (next.inserted > 0) {

                Difference.Insert diff;
                if (nodesA[start0].nodeType == XMLStreamReader.END_ELEMENT)
                    diff = new Difference.Append(new NodeProxy(docA, nodesA[start0].nodeId), docB);
                else
                    diff = new Difference.Insert(new NodeProxy(docA, nodesA[start0].nodeId), docB);
                
                // now scan the chunk and collect the nodes into a node set
                DiffNode[] nodes = new DiffNode[last - start];
                int j = 0;
                for (int i = start; i < last; i++, j++) {
                    if (LOG.isTraceEnabled())
                        LOG.trace(Integer.toString(i) + " " + nodesB[i]);
                    nodes[j] = nodesB[i];
                }
                diff.setNodes(nodes);
                changes.add(diff);
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
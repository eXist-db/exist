package org.exist.versioning;

import bmsi.util.Diff;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.TreeMap;

public class XMLDiff {

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
            DiffNode[] nodesA = getNodes(broker, root);
            root = (ElementImpl) docB.getDocumentElement();
            DiffNode[] nodesB = getNodes(broker, root);

//            System.out.println("Source:");
//            debugNodes(nodesA);
//            System.out.println("Modified:");
//            debugNodes(nodesB);

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
        for (int i = 0; i < nodes.length; i++) {
            DiffNode node = nodes[i];
            System.out.println(Integer.toString(i) + " " + node);
        }
    }

    protected List simplify(List changes) {
        List simplified = new ArrayList(changes.size());
        Map map = new TreeMap();
        for (int i = 0; i < changes.size(); i++) {
            Difference diff = (Difference) changes.get(i);
            if (diff.type == Difference.INSERT) {
                NodeId nodeId = diff.refChild.getNodeId();
                if (map.containsKey(nodeId)) {
                    Difference.Insert diff2 = (Difference.Insert) map.get(nodeId);
                    diff2.addNodes(((Difference.Insert) diff).nodes);
                } else
                    map.put(nodeId, diff);
            } else {
                simplified.add(diff);
            }
        }
        for (Iterator i = map.values().iterator(); i.hasNext();) {
            Difference diff = (Difference) i.next();
            simplified.add(diff);
        }
        return simplified;
    }

    protected String diff2XML(List changes, Properties properties) throws DiffException {
        changes = simplify(changes);
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
        NodeSet deletedNodes = new NewArrayNodeSet(1, 16);
        Diff.change next = script;
//        System.out.println("Modifications:");
        while (next != null) {
            int start0 = next.line0;
            int start = next.line1;
            int last = start + next.inserted;

            Stack elementStack = new Stack();

            // sanitize the diff: move the start and end offsets of the chunk to properly include
            // all required start and end tags.

            if (next.inserted > 0) {
                // step 1: at the end of the chunk, remove start tags with missing end tags
                for (int i = last - 1; i >= start; i--) {
                    if (nodesB[i].nodeType == XMLStreamReader.START_ELEMENT) {
                        System.out.println("Found element out of context at end of chunk: " + nodesB[i].value);
                        last--;
                    } else
                        break;
                }
                // step 2: search for end tags with missing start tags. adjust start to include the start tags.
                // this may need to be done for more than one tag.
                boolean needRescan;
                do {
                    needRescan = false;
                    for (int i = start; i < last; i++) {
                        if (nodesB[i].nodeType == XMLStreamReader.START_ELEMENT) {
                            elementStack.push(nodesB[i]);
                        } else if (nodesB[i].nodeType == XMLStreamReader.END_ELEMENT) {
                            if (elementStack.isEmpty()) {
                                System.out.println("Found out of context element: " + nodesB[i].value);
                                for (int j = start - 1; j > -1; j--) {
                                    if (nodesB[j].nodeId.equals(nodesB[i].nodeId)) {
                                        start0 = start0 - (start - j);
                                        start = j;
                                        last = start + next.inserted;
                                        needRescan = true;
                                    }
                                }
                            } else {
                                DiffNode n = (DiffNode) elementStack.pop();
                                if (!n.nodeId.equals(nodesB[i].nodeId))
                                    throw new XMLStreamException("Diff error: element is out of context: " + nodesB[i].value);
                            }
                        }
                    }
                    elementStack.clear();
                } while (needRescan);
            } else if (next.deleted > 0) {
                last = start0 + next.deleted;
                // step 1: at the end of the chunk, remove start tags with missing end tags
                for (int i = last - 1; i >= start0; i--) {
                    if (nodesA[i].nodeType == XMLStreamReader.START_ELEMENT) {
                        System.out.println("Found element out of context at end of chunk: " + nodesA[i].value);
                        last--;
                    } else
                        break;
                }
                // step 2: search for end tags with missing start tags. adjust start to include the start tags.
                // this may need to be done for more than one tag.
                boolean needRescan;
                do {
                    needRescan = false;
                    for (int i = start0; i < last; i++) {
                        if (nodesA[i].nodeType == XMLStreamReader.START_ELEMENT) {
                            elementStack.push(nodesA[i]);
                        } else if (nodesA[i].nodeType == XMLStreamReader.END_ELEMENT) {
                            if (elementStack.isEmpty()) {
                                System.out.println("Found out of context element: " + nodesA[i].value);
                                for (int j = start0 - 1; j > -1; j--) {
                                    if (nodesA[j].nodeId.equals(nodesA[i].nodeId)) {
                                        start0 = j;
                                        last = start0 + next.deleted;
                                        needRescan = true;
                                    }
                                }
                            } else {
                                DiffNode n = (DiffNode) elementStack.pop();
                                if (!n.nodeId.equals(nodesA[i].nodeId))
                                    throw new XMLStreamException("Diff error: element is out of context: " + nodesB[i].value);
                            }
                        }
                    }
                    elementStack.clear();
                } while (needRescan);
            }

           if (next.inserted > 0) {
//               System.out.println("Insertion next.line1 = " + next.line1 + " next.inserted = " + next.inserted +
//                "\nnext.line0 = " + next.line0 + " next.deleted = " + next.deleted);


               Difference.Insert diff;
               if (nodesA[start0].nodeType == XMLStreamReader.END_ELEMENT)
                   diff = new Difference.Append(new NodeProxy(docA, nodesA[start0].nodeId));
               else
                   diff = new Difference.Insert(new NodeProxy(docA, nodesA[start0].nodeId));

               // now scan the chunk and collect the nodes into a node set
               NodeSet insertedNodes = new NewArrayNodeSet(1, 8);
               for (int i = start; i < last; i++) {
//                   System.out.println(Integer.toString(i) + " " + nodesB[i]);
                   NodeId nodeId = nodesB[i].nodeId;
                   NodeProxy p = new NodeProxy(docB, nodeId, (short)
                           (nodesB[i].nodeType == XMLStreamReader.ATTRIBUTE ? Type.ATTRIBUTE : Type.NODE));
                   if (nodesB[i].nodeType == XMLStreamReader.END_ELEMENT) {
                       elementStack.pop();
                       insertedNodes.add(p);
                   } else if (nodesB[i].nodeType == XMLStreamReader.START_ELEMENT) {
                       elementStack.push(nodesB[i]);
                   } else
                       insertedNodes.add(p);
               }

               // handle all elements which were not properly closed
               while (!elementStack.isEmpty()) {
                   DiffNode n = (DiffNode) elementStack.pop();
                   insertedNodes.add(new NodeProxy(docB, n.nodeId));
               }

               // scan the node set and keep the top nodes only. filter out all descendants.
               NodeSet filtered = new NewArrayNodeSet(1, insertedNodes.getItemCount());
               try {
                   for (SequenceIterator i = insertedNodes.iterate(); i.hasNext(); ) {
                       NodeProxy p = (NodeProxy) i.nextItem();
                       if (filtered.parentWithChild(p.getDocument(), p.getNodeId(), false, true) == null)
                           filtered.add(p);
                   }
               } catch (XPathException e) {
                   e.printStackTrace();
               }
               // finally add the node set to the Difference
               diff.setNodes(filtered);
               // and the Difference to the changes
               changes.add(diff);
           }
           
           if (next.deleted > 0) {
               if (next.inserted > 0)
                   last = start0 + next.deleted;
//               System.out.println("Deleted: " + start0 + " last: " + last);
               for (int i = start0; i < last; i++) {
                   NodeId nodeId = nodesA[i].nodeId;
                   NodeProxy p = new NodeProxy(docA, nodeId);
                   deletedNodes.add(p);
               }
           }
           next = next.link;
        }
        processDeleted(deletedNodes, changes);
        return changes;
    }

    protected void processDeleted(NodeSet nodes, List changes) {
        NodeSet filtered = new NewArrayNodeSet(1, nodes.getItemCount());
        try {
            for (SequenceIterator i = nodes.iterate(); i.hasNext();) {
                NodeProxy p = (NodeProxy) i.nextItem();
                if (filtered.parentWithChild(p.getDocument(), p.getNodeId(), false, true) == null) {
                    filtered.add(p);
                    Difference.Delete diff = new Difference.Delete(p);
                    changes.add(diff);
                }
            }
        } catch (XPathException e) {
            e.printStackTrace();
        }
    }

    protected DiffNode[] getNodes(DBBroker broker, ElementImpl root) throws XMLStreamException, IOException {
        EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(root, false);
        List nodes = new ArrayList();
        DiffNode node;
        while (reader.hasNext()) {
            int status = reader.next();
            NodeId nodeId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
            switch (status) {
                case XMLStreamReader.START_ELEMENT:
                    node = new DiffNode(nodeId, status, reader.getQName().getStringValue());
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
                    node = new DiffNode(nodeId, status, "/" + reader.getQName().getStringValue());
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

    private static class DiffNode {

        int diffType;

        NodeId nodeId;
        int nodeType;
        String value;

        public DiffNode(NodeId nodeId, int nodeType, String value) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.value = value;
        }

        public boolean equals(Object obj) {
            DiffNode other = (DiffNode) obj;
            if (nodeType != other.nodeType)
                return false;
            return value.equals(other.value);
        }

        public int hashCode() {
            return (value.hashCode() << 1) + nodeType;
        }

        public String toString() {
            return nodeType + " " + nodeId.toString() + " " + value;
        }
    }
}
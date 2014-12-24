package org.exist.versioning;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.AttrList;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamReader;

public abstract class Difference implements Comparable<Difference> {

    public final static int INSERT = 0;
    public final static int DELETE = 1;
    public final static int APPEND = 2;
    public final static int UPDATE = 3;

    public final static QName ELEMENT_INSERT = new QName("insert", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ATTR_REF = new QName("ref", "", "");
    public final static QName ATTR_NAMESPACE = new QName("namespace", "", "");
    public final static QName ATTR_NAME = new QName("name", "", "");
    public final static QName ATTR_EVENT = new QName("event", "", "");
    public final static QName ELEMENT_ATTRIBUTE = new QName("attribute", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ELEMENT_START = new QName("start", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ELEMENT_END = new QName("end", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ELEMENT_COMMENT = new QName("comment", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ELEMENT_APPEND = new QName("append", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ELEMENT_DELETE = new QName("delete", StandardDiff.NAMESPACE, StandardDiff.PREFIX);

    protected int type;
    protected NodeProxy refChild;

    public Difference(int type, NodeProxy reference) {
        this.type = type;
        this.refChild = reference;
    }

    public abstract void serialize(DBBroker broker, Receiver handler);

    public boolean equals(Object obj) {
        Difference other = (Difference) obj;
        return refChild.getNodeId().equals(other.refChild.getNodeId());
    }

    public int compareTo(Difference other) {
        return refChild.getNodeId().compareTo(other.refChild.getNodeId());
    }

    public static class Insert extends Difference {

        protected DocumentImpl otherDoc;
        protected DiffNode[] nodes;

        public Insert(NodeProxy reference, DocumentImpl otherDoc) {
            super(INSERT, reference);
            this.otherDoc = otherDoc;
        }

        public Insert(int type, NodeProxy reference, DocumentImpl otherDoc) {
            super(type, reference);
            this.otherDoc = otherDoc;
        }

        protected void addNodes(DiffNode[] nodesToAdd) {
            if (nodes == null)
                nodes = nodesToAdd;
            else {
                DiffNode n[] = new DiffNode[nodes.length + nodesToAdd.length];
                System.arraycopy(nodes, 0, n, 0, nodes.length);
                System.arraycopy(nodesToAdd, 0, n, nodes.length, nodesToAdd.length);
                nodes = n;
            }
        }

        public void serialize(DBBroker broker, Receiver handler) {
            try {
                AttrList attribs = new AttrList();
                attribs.addAttribute(ATTR_REF, refChild.getNodeId().toString());
                handler.startElement(ELEMENT_INSERT, attribs);
                serializeChildren(broker, handler);
                handler.endElement(ELEMENT_INSERT);
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        protected void serializeChildren(DBBroker broker, Receiver handler) throws SAXException {
            AttrList attribs;
            for (int i = 0; i < nodes.length; i++) {
                switch (nodes[i].nodeType) {
                    case XMLStreamReader.ATTRIBUTE:
                        attribs = new AttrList();
                        AttrImpl attr = (AttrImpl) broker.objectWith(otherDoc, nodes[i].nodeId);
                        attribs.addAttribute(new QName(attr.getLocalName(), attr.getNamespaceURI(), attr.getPrefix()),
                                attr.getValue(), attr.getType());
                        handler.startElement(ELEMENT_ATTRIBUTE, attribs);
                        handler.endElement(ELEMENT_ATTRIBUTE);
                        break;
                    case XMLStreamReader.START_ELEMENT:
                        // check if there's a complete element to write, not just a start or end tag
                        // if yes, just copy the element, if no, write a start-tag node
                        boolean isClosed = false;
                        NodeId nodeId = nodes[i].nodeId;
                        for (int j = i; j < nodes.length; j++) {
                            if (nodes[j].nodeType == XMLStreamReader.END_ELEMENT &&
                                    nodes[j].nodeId.equals(nodeId)) {
                                isClosed = true;
                                NodeProxy proxy = new NodeProxy(otherDoc, nodes[i].nodeId);
                                Serializer serializer = broker.getSerializer();
                                serializer.reset();
                                serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                                serializer.setReceiver(handler);
                                serializer.toReceiver(proxy, false);
                                i = j;
                                break;
                            }
                        }
                        if (!isClosed) {
                            attribs = new AttrList();
                            if (nodes[i].qname.hasNamespace())
                                attribs.addAttribute(ATTR_NAMESPACE, nodes[i].qname.getNamespaceURI());
                            attribs.addAttribute(ATTR_NAME, nodes[i].qname.getStringValue());
                            handler.startElement(ELEMENT_START, attribs);
                            handler.endElement(ELEMENT_START);
                        }
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        attribs = new AttrList();
                        if (nodes[i].qname.hasNamespace())
                            attribs.addAttribute(ATTR_NAMESPACE, nodes[i].qname.getNamespaceURI());
                        attribs.addAttribute(ATTR_NAME, nodes[i].qname.getStringValue());
                        handler.startElement(ELEMENT_END, attribs);
                        handler.endElement(ELEMENT_END);
                        break;
                    case XMLStreamReader.COMMENT:
                        attribs = new AttrList();
                        handler.startElement(ELEMENT_COMMENT, attribs);
                        handler.characters(nodes[i].value);
                        handler.endElement(ELEMENT_COMMENT);
                        break;
                    default:
                        NodeProxy proxy = new NodeProxy(otherDoc, nodes[i].nodeId);
                        Serializer serializer = broker.getSerializer();
                        serializer.reset();
                        serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                        serializer.setReceiver(handler);
                        serializer.toReceiver(proxy, false);
                        break;
                }
            }
        }
    }

    public final static class Append extends Insert {

        public Append(NodeProxy reference, DocumentImpl otherDoc) {
            super(APPEND, reference, otherDoc);
        }

        public void serialize(DBBroker broker, Receiver handler) {
            try {
                AttrList attribs = new AttrList();
                attribs.addAttribute(ATTR_REF, refChild.getNodeId().toString());
                handler.startElement(ELEMENT_APPEND, attribs);
                serializeChildren(broker, handler);
                handler.endElement(ELEMENT_APPEND);
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    public final static class Delete extends Difference {

        protected int event = -1;

        public Delete(NodeProxy reference) {
            this(-1, reference);
        }

        public Delete(int event, NodeProxy reference) {
            super(DELETE, reference);
            this.event = event;
        }

        public void serialize(DBBroker broker, Receiver handler) {
            try {
                AttrList attribs = new AttrList();
                if (event == XMLStreamReader.START_ELEMENT || event == XMLStreamReader.END_ELEMENT) {
                    String ev = event == XMLStreamReader.START_ELEMENT ? "start" : "end";
                    attribs.addAttribute(ATTR_EVENT, ev);
                }
                attribs.addAttribute(ATTR_REF, refChild.getNodeId().toString());
                handler.startElement(ELEMENT_DELETE, attribs);
                handler.endElement(ELEMENT_DELETE);
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
package org.exist.versioning;

import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.Namespaces;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamReader;
import java.io.StringWriter;
import java.util.Iterator;

public abstract class Difference {

    public final static int INSERT = 0;
    public final static int DELETE = 1;
    public final static int APPEND = 2;
    public final static int UPDATE = 3;

    protected int type;
    protected NodeProxy refChild;

    public Difference(int type, NodeProxy reference) {
        this.type = type;
        this.refChild = reference;
    }

    public abstract void serialize(DBBroker broker, ContentHandler handler);

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

        protected void setNodes(DiffNode[] nodes) {
            this.nodes = nodes;
        }

        public void serialize(DBBroker broker, ContentHandler handler) {
            try {
                AttributesImpl attribs = new AttributesImpl();
                attribs.addAttribute("", "ref", "ref", "CDATA", refChild.getNodeId().toString());
                handler.startElement(XMLDiff.NAMESPACE, "insert", XMLDiff.PREFIX + ":insert", attribs);
                serializeChildren(broker, handler, attribs);
                handler.endElement(XMLDiff.NAMESPACE, "insert", XMLDiff.PREFIX + ":insert");
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        protected void serializeChildren(DBBroker broker, ContentHandler handler, AttributesImpl attribs) throws SAXException {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i].nodeType == XMLStreamReader.ATTRIBUTE) {
                    AttrImpl attr = (AttrImpl) broker.objectWith(otherDoc, nodes[i].nodeId);
                    attribs.clear();
                    attribs.addAttribute(attr.getNamespaceURI(), attr.getLocalName(), attr.getName(),
                            AttrImpl.getAttributeType(attr.getType()), attr.getValue());
                    handler.startElement(XMLDiff.NAMESPACE, "attribute", XMLDiff.PREFIX + ":attribute", attribs);
                    handler.endElement(XMLDiff.NAMESPACE, "attribute", XMLDiff.PREFIX + ":attribute");
                } else if (nodes[i].nodeType == XMLStreamReader.START_ELEMENT) {
                    // check if there's a complete element to write, not just a start or end tag
                    // if yes, just copy the element, if no, write a start-tag node
                    boolean isClosed = false;
                    NodeId nodeId = nodes[i].nodeId;
                    for (int j = i; j < nodes.length; j++) {
                        if (nodes[j].nodeType == XMLStreamReader.END_ELEMENT &&
                                nodes[j].nodeId.equals(nodeId)) {
                            isClosed = true;
                            NodeProxy proxy = new NodeProxy(otherDoc, nodes[i].nodeId);
                            proxy.toSAX(broker, handler, null);
                            i = j;
                            break;
                        }
                    }
                    if (!isClosed) {
                        attribs.clear();
                        if (nodes[i].qname.needsNamespaceDecl())
                            attribs.addAttribute("", "namespace", "namespace", "CDATA", nodes[i].qname.getNamespaceURI());
                        attribs.addAttribute("", "name", "name", "CDATA", nodes[i].qname.getStringValue());
                        handler.startElement(XMLDiff.NAMESPACE, "start", XMLDiff.PREFIX + ":start", attribs);
                        handler.endElement(XMLDiff.NAMESPACE, "start", XMLDiff.PREFIX + ":start");
                    }
                } else if (nodes[i].nodeType == XMLStreamReader.END_ELEMENT) {
                    attribs.clear();
                    if (nodes[i].qname.needsNamespaceDecl())
                        attribs.addAttribute("", "namespace", "namespace", "CDATA", nodes[i].qname.getNamespaceURI());
                    attribs.addAttribute("", "name", "name", "CDATA", nodes[i].qname.getStringValue());
                    handler.startElement(XMLDiff.NAMESPACE, "end", XMLDiff.PREFIX + ":end", attribs);
                    handler.endElement(XMLDiff.NAMESPACE, "end", XMLDiff.PREFIX + ":end");
                } else {
                    NodeProxy proxy = new NodeProxy(otherDoc, nodes[i].nodeId);
                    proxy.toSAX(broker, handler, null);
                }
            }
        }
    }

    public final static class Append extends Insert {

        public Append(NodeProxy reference, DocumentImpl otherDoc) {
            super(APPEND, reference, otherDoc);
        }

        public void serialize(DBBroker broker, ContentHandler handler) {
            try {
                AttributesImpl attribs = new AttributesImpl();
                attribs.addAttribute("", "ref", "ref", "CDATA", refChild.getNodeId().toString());
                handler.startElement(XMLDiff.NAMESPACE, "append", XMLDiff.PREFIX + ":append", attribs);
                serializeChildren(broker, handler, attribs);
                handler.endElement(XMLDiff.NAMESPACE, "append", XMLDiff.PREFIX + ":append");
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

        public void serialize(DBBroker broker, ContentHandler handler) {
            try {
                AttributesImpl attribs = new AttributesImpl();
                if (event == XMLStreamReader.START_ELEMENT || event == XMLStreamReader.END_ELEMENT) {
                    String ev = event == XMLStreamReader.START_ELEMENT ? "start" : "end";
                    attribs.addAttribute("", "event", "event", "CDATA", ev);
                }
                attribs.addAttribute("", "ref", "ref", "CDATA", refChild.getNodeId().toString());
                handler.startElement(XMLDiff.NAMESPACE, "delete", XMLDiff.PREFIX + ":delete", attribs);
                handler.endElement(XMLDiff.NAMESPACE, "delete", XMLDiff.PREFIX + ":delete");
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    public final static class Update extends Difference {

        private NodeId endId;

        public Update(NodeProxy reference, NodeId endId) {
            super(UPDATE, reference);
            this.endId = endId;
        }

        public void serialize(DBBroker broker, ContentHandler handler) {
            try {
                AttributesImpl attribs = new AttributesImpl();
                attribs.addAttribute("", "ref", "ref", "CDATA", refChild.getNodeId().toString());
                attribs.addAttribute("", "end", "end", "CDATA", endId.toString());
                handler.startElement(XMLDiff.NAMESPACE, "update", XMLDiff.PREFIX + ":update", attribs);
                handler.endElement(XMLDiff.NAMESPACE, "update", XMLDiff.PREFIX + ":update");
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
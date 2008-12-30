package org.exist.versioning;

import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.AttrImpl;
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

import java.io.StringWriter;
import java.util.Iterator;

public abstract class Difference {

    public final static int INSERT = 0;
    public final static int DELETE = 1;
    public final static int APPEND = 2;

    protected int type;
    protected NodeProxy refChild;

    public Difference(int type, NodeProxy reference) {
        this.type = type;
        this.refChild = reference;
    }

    public abstract void serialize(DBBroker broker, ContentHandler handler);

    public static class Insert extends Difference {

        protected NodeSet nodes = new NewArrayNodeSet(1, 8);

        public Insert(NodeProxy reference) {
            super(INSERT, reference);
        }

        public Insert(int type, NodeProxy reference) {
            super(type, reference);
        }

        protected void addNode(NodeProxy node) {
            if (nodes.parentWithChild(node.getDocument(), node.getNodeId(), false, true) == null)
                nodes.add(node);
        }

        protected void addNodes(NodeSet set) {
            nodes.addAll(set);
        }
        
        protected void setNodes(NodeSet nodes) {
            this.nodes = nodes;
        }

        public void serialize(DBBroker broker, ContentHandler handler) {
            try {
                AttributesImpl attribs = new AttributesImpl();
                attribs.addAttribute("", "ref", "ref", "CDATA", refChild.getNodeId().toString());
                handler.startElement(XMLDiff.NAMESPACE, "insert", XMLDiff.PREFIX + ":insert", attribs);
                for (SequenceIterator i = nodes.iterate(); i.hasNext(); ) {
                    NodeProxy proxy = (NodeProxy) i.nextItem();
                    if (proxy.getType() == Type.ATTRIBUTE) {
                        AttrImpl attr = (AttrImpl) proxy.getNode();
                        attribs.clear();
                        attribs.addAttribute(attr.getNamespaceURI(), attr.getLocalName(), attr.getName(),
                                AttrImpl.getAttributeType(attr.getType()), attr.getValue());
                        handler.startElement(XMLDiff.NAMESPACE, "attribute", XMLDiff.PREFIX + ":attribute", attribs);
                        handler.endElement(XMLDiff.NAMESPACE, "attribute", XMLDiff.PREFIX + ":attribute");
                    } else
                        proxy.toSAX(broker, handler, null);
                }
                handler.endElement(XMLDiff.NAMESPACE, "insert", XMLDiff.PREFIX + ":insert");
            } catch (XPathException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    public final static class Append extends Insert {

        public Append(NodeProxy reference) {
            super(APPEND, reference);
        }

        public void serialize(DBBroker broker, ContentHandler handler) {
            try {
                AttributesImpl attribs = new AttributesImpl();
                attribs.addAttribute("", "ref", "ref", "CDATA", refChild.getNodeId().toString());
                handler.startElement(XMLDiff.NAMESPACE, "append", XMLDiff.PREFIX + ":append", attribs);
                for (SequenceIterator i = nodes.iterate(); i.hasNext(); ) {
                    NodeProxy proxy = (NodeProxy) i.nextItem();
                    if (proxy.getType() == Type.ATTRIBUTE) {
                        AttrImpl attr = (AttrImpl) proxy.getNode();
                        attribs.clear();
                        attribs.addAttribute(attr.getNamespaceURI(), attr.getLocalName(), attr.getName(),
                                AttrImpl.getAttributeType(attr.getType()), attr.getValue());
                        handler.startElement(XMLDiff.NAMESPACE, "attribute", XMLDiff.PREFIX + ":attribute", attribs);
                        handler.endElement(XMLDiff.NAMESPACE, "attribute", XMLDiff.PREFIX + ":attribute");
                    } else
                        proxy.toSAX(broker, handler, null);
                }
                handler.endElement(XMLDiff.NAMESPACE, "append", XMLDiff.PREFIX + ":append");
            } catch (XPathException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    public final static class Delete extends Difference {

        public Delete(NodeProxy reference) {
            super(DELETE, reference);
        }

        public void serialize(DBBroker broker, ContentHandler handler) {
            try {
                AttributesImpl attribs = new AttributesImpl();
                attribs.addAttribute("", "ref", "ref", "CDATA", refChild.getNodeId().toString());
                handler.startElement(XMLDiff.NAMESPACE, "delete", XMLDiff.PREFIX + ":delete", attribs);
                handler.endElement(XMLDiff.NAMESPACE, "delete", XMLDiff.PREFIX + ":delete");
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
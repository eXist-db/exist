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
package org.exist.versioning;

import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.security.xacml.AccessContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;

/**
 * Patch a given source document by applying a diff in eXist's diff format.
 */
public class Patch {

    private final static String D_START = "start";
    private final static String D_END = "end";
    private final static String D_BOTH = "both";
    private final static String D_SUBTREE = "subtree";
    
    private DBBroker broker;

    private Map deletedNodes = null;
    private Map insertedNodes = null;
    private Map appendedNodes = null;

    /**
     * Create a new Patch instance using the specified broker and diff document.
     *
     * @param broker the DBBroker to use
     * @param diff the diff document to apply
     *
     * @throws XPathException
     */
    public Patch(DBBroker broker, DocumentImpl diff) throws XPathException {
        this.broker = broker;
        parseDiff(broker, diff);
    }

    /**
     * Apply the diff to the given source data stream passed as an XMLStreamReader. Write
     * output to the specified receiver.
     *
     * @throws DiffException
     */
    public void patch(ExtendedXMLStreamReader reader, Receiver receiver) throws DiffException {
        try {
            NodeId skipSubtree = null;
            while (reader.hasNext()) {
                int status = reader.next();
                NodeId nodeId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
                if (status != XMLStreamReader.END_ELEMENT) {
                    ElementImpl insertedNode = (ElementImpl) insertedNodes.get(nodeId);
                    if (insertedNode != null) {
                        insertNode(insertedNode, receiver);
                    }
                } else {
                    ElementImpl appendedNode = (ElementImpl) appendedNodes.get(nodeId);
                    if (appendedNode != null) {
                        insertNode(appendedNode, receiver);
                    }
                }
                String opt = (String) deletedNodes.get(nodeId);
                if (opt == D_SUBTREE) {
                    if (status == XMLStreamReader.START_ELEMENT)
                        skipSubtree = nodeId;
                } else if (opt == D_BOTH) {
                    //skip
                } else if (opt == D_END && status == XMLStreamReader.END_ELEMENT) {
                    // skip
                } else if (opt == D_START && status == XMLStreamReader.START_ELEMENT) {
                    // skip
                } else if (skipSubtree == null)
                    copyNode(reader, receiver, status);
                if (status == XMLStreamReader.END_ELEMENT && skipSubtree != null &&
                        skipSubtree.equals(nodeId))
                    skipSubtree = null;
            }
        } catch (XMLStreamException e) {
            throw new DiffException("Caught exception while reading source document for patch: " +
                    e.getMessage(), e);
        } catch (IOException e) {
            throw new DiffException("Caught exception while patching document: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new DiffException("Caught exception while serializing patch output: " + e.getMessage(), e);
        }
    }

    private void insertNode(StoredNode insertedNode, Receiver receiver) throws XMLStreamException, IOException, SAXException {
        ExtendedXMLStreamReader reader = broker.newXMLStreamReader(insertedNode, false);
        reader.next();
        while (reader.hasNext()) {
            int status = reader.next();
            if ((status == XMLStreamReader.START_ELEMENT || status == XMLStreamReader.END_ELEMENT) &&
                    XMLDiff.NAMESPACE.equals(reader.getNamespaceURI())) {
                if (status == XMLStreamReader.START_ELEMENT) {
                    if ("attribute".equals(reader.getLocalName())) {
                        int attrCount = reader.getAttributeCount();
                        for (int i = 0; i < attrCount; i++) {
                            QName qname = reader.getAttributeQName(i);
                            receiver.attribute(qname, reader.getAttributeValue(i));
                        }
                    } else if ("start".equals(reader.getLocalName())) {
                        String namespace = reader.getAttributeValue("", "namespace");
                        String name = reader.getAttributeValue("", "name");
                        receiver.startElement(new QName(QName.extractLocalName(name), namespace, QName.extractPrefix(name)), null);
                    } else if ("end".equals(reader.getLocalName())) {
                        String namespace = reader.getAttributeValue("", "namespace");
                        String name = reader.getAttributeValue("", "name");
                        receiver.endElement(new QName(QName.extractLocalName(name), namespace, QName.extractPrefix(name)));
                    }
                }
            } else {
                copyNode(reader, receiver, status);
            }
        }
    }

    private void copyNode(ExtendedXMLStreamReader reader, Receiver receiver, int status) throws SAXException, XMLStreamException, IOException {
        switch (status) {
            case XMLStreamReader.START_ELEMENT:
                AttrList attrs = new AttrList();
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    // check if an attribute has to be inserted before the current attribute
                    NodeId nodeId = reader.getAttributeId(i);

                    // check if an attribute has to be inserted before the current attribute
                    ElementImpl insertedNode = (ElementImpl) insertedNodes.get(nodeId);
                    if (insertedNode != null) {
                        StoredNode child = (StoredNode) insertedNode.getFirstChild();
                        while (child != null) {
                            if (XMLDiff.NAMESPACE.equals(child.getNamespaceURI()) && "attribute".equals(child.getLocalName())) {
                                NamedNodeMap map = child.getAttributes();
                                for (int j = 0; j < map.getLength(); j++) {
                                    AttrImpl attr = (AttrImpl) map.item(j);
                                    if (!attr.getName().startsWith("xmlns"))
                                        attrs.addAttribute(attr.getQName(), attr.getValue(),
                                                attr.getType(), attr.getNodeId());
                                }
                            }
                            child = (StoredNode) child.getNextSibling();
                        }
                    }

                    if (deletedNodes.get(nodeId) == null) {
                        QName attrQn = new QName(reader.getAttributeLocalName(i), reader.getAttributeNamespace(i),
                                reader.getAttributePrefix(i));
                        attrs.addAttribute(
                                attrQn,
                                reader.getAttributeValue(i),
                                getAttributeType(reader.getAttributeType(i))
                        );
                    }
                }
                receiver.startElement(new QName(reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix()),
                        attrs);
                break;
            case XMLStreamReader.END_ELEMENT:
                receiver.endElement(new QName(reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix()));
                break;
            case XMLStreamReader.CHARACTERS:
                receiver.characters(reader.getText());
                break;
            case XMLStreamReader.CDATA:
                char[] cdata = reader.getTextCharacters();
                receiver.cdataSection(cdata, 0, cdata.length);
                break;
            case XMLStreamReader.PROCESSING_INSTRUCTION:
                receiver.processingInstruction(reader.getPITarget(), reader.getPIData());
                break;
            case XMLStreamReader.COMMENT:
                char[] ch = reader.getTextCharacters();
                receiver.comment(ch, 0, ch.length);
                break;
        }
    }

    private void parseDiff(DBBroker broker, DocumentImpl doc) throws XPathException {
        deletedNodes = new TreeMap();
        insertedNodes = new TreeMap();
        appendedNodes = new TreeMap();
        XQuery service = broker.getXQueryService();
        Sequence changes = service.execute("declare namespace v=\"http://exist-db.org/versioning\";" +
                "doc('" + doc.getURI().toString() + "')/v:version/*",
                Sequence.EMPTY_SEQUENCE, AccessContext.TEST);
        for (SequenceIterator i = changes.iterate(); i.hasNext(); ) {
            NodeProxy p = (NodeProxy) i.nextItem();
            Element child = (Element) p.getNode();
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    child.getNamespaceURI().equals(XMLDiff.NAMESPACE)) {
               NodeId id = parseRef(broker, child, "ref");
               if (child.getLocalName().equals("delete")) {
                   String event = ((Element) child).getAttribute("event");
                   if (event == null || event.length() == 0)
                       deletedNodes.put(id, D_SUBTREE);
                   else if ("both".equals(event))
                       deletedNodes.put(id, D_BOTH);
                   else if ("start".equals(event)) {
                       String opt = (String) deletedNodes.get(id);
                       if (opt == D_END)
                           deletedNodes.put(id, D_BOTH);
                       else
                           deletedNodes.put(id, D_START);
                   } else {
                       String opt = (String) deletedNodes.get(id);
                       if (opt == D_START)
                           deletedNodes.put(id, D_BOTH);
                       else
                           deletedNodes.put(id, D_END);
                   }
               } else if (child.getLocalName().equals("insert")) {
                   insertedNodes.put(id, child);
               } else if (child.getLocalName().equals("append")) {
                   appendedNodes.put(id, child);
               }
            }
        }
    }

    private NodeId parseRef(DBBroker broker, Node child, String attr) {
        String idval = ((Element)child).getAttribute(attr);
        return broker.getBrokerPool().getNodeFactory().createFromString(idval);
    }

    private int getAttributeType(String attributeType) {
        if ("ID".equals(attributeType))
            return AttrImpl.ID;
        else if ("IDREF".equals(attributeType))
            return AttrImpl.IDREF;
        else if ("IDREFS".equals(attributeType))
            return AttrImpl.IDREFS;
        else
            return AttrImpl.CDATA;
    }
}

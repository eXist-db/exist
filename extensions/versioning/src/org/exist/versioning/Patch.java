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
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.XPathException;
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

    private DBBroker broker;
    private Set deletedNodes = null;
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
            NodeId skip = null;
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
                if (status == XMLStreamReader.END_ELEMENT && skip != null && nodeId.equals(skip))
                    skip = null;
                else if (deletedNodes.contains(nodeId)) {
                    if (status == XMLStreamReader.START_ELEMENT)
                        skip = nodeId;
                } else if (skip == null)
                    copyNode(reader, receiver, status);
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

    private void insertNode(Element insertedNode, Receiver receiver) throws XMLStreamException, IOException, SAXException {
        StoredNode child = (StoredNode) insertedNode.getFirstChild();
        while (child != null) {
            if (XMLDiff.NAMESPACE.equals(child.getNamespaceURI()) && "attribute".equals(child.getLocalName())) {
                NamedNodeMap attrs = child.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    AttrImpl attr = (AttrImpl) attrs.item(i);
                    if (!attr.getName().startsWith("xmlns"))
                        receiver.attribute(attr.getQName(), attr.getValue());
                }
            } else {
                ExtendedXMLStreamReader reader = broker.newXMLStreamReader(child, false);
                while (reader.hasNext()) {
                    int status = reader.next();
                    copyNode(reader, receiver, status);
                }
            }
            child = (StoredNode) child.getNextSibling();
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

                    if (!deletedNodes.contains(nodeId)) {
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
        deletedNodes = new TreeSet();
        insertedNodes = new TreeMap();
        appendedNodes = new TreeMap();

        Element root = doc.getDocumentElement();
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    child.getNamespaceURI().equals(XMLDiff.NAMESPACE)) {
               if (child.getLocalName().equals("delete")) {
                   NodeId id = parseRef(broker, child);
                   deletedNodes.add(id);
               } else if (child.getLocalName().equals("insert")) {
                   NodeId id = parseRef(broker, child);
                   insertedNodes.put(id, child);
               } else if (child.getLocalName().equals("append")) {
                   NodeId id = parseRef(broker, child);
                   appendedNodes.put(id, child);
               }
            }
            child = child.getNextSibling();
        }
    }

    private NodeId parseRef(DBBroker broker, Node child) {
        String idval = ((Element)child).getAttribute("ref");
        NodeId id = broker.getBrokerPool().getNodeFactory().createFromString(idval);
        return id;
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

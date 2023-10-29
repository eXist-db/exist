/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xqj;

import org.exist.dom.QName;
import org.exist.dom.memtree.*;
import org.exist.storage.DBBroker;
import org.exist.xquery.Expression;
import org.exist.xquery.NameTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItemType;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;


/**
 * A utility class that provides marshalling services for external variables and methods 
 * to create DOM Nodes from streamed representation.
 * 
 * @author Wolfgang Meier
 *
 */
public class Marshaller {

    public final static String NAMESPACE = "http://exist-db.org/xquery/types/serialized";
    public final static String PREFIX = "sx";

    
    private final static Properties OUTPUT_PROPERTIES = new Properties();

    private final static String VALUE_ELEMENT = "value";
    private final static String VALUE_ELEMENT_QNAME = PREFIX + ":value";
    private final static QName VALUE_QNAME = new QName(VALUE_ELEMENT,  NAMESPACE, PREFIX);
    
    private final static String SEQ_ELEMENT = "sequence";
    private final static String SEQ_ELEMENT_QNAME = PREFIX + ":sequence";
    
    private final static String ATTR_TYPE = "type";
    private final static String ATTR_ITEM_TYPE = "item-type";

    public final static QName ROOT_ELEMENT_QNAME = new QName(SEQ_ELEMENT, NAMESPACE, PREFIX);
    
    /**
     * Marshall a sequence in an xml based string representation.
     *
     * @param broker the database broker
     * @param seq Sequence to be marshalled
     * @param handler Content handler for building the resulting string
     *
     * @throws XPathException if an XPath error occurs
     * @throws SAXException if a SAX parsing exception occurs
     */
    public static void marshall(final DBBroker broker, final Sequence seq, final ContentHandler handler)
            throws XPathException, SAXException {
        final AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", ATTR_ITEM_TYPE, ATTR_ITEM_TYPE, "CDATA", Type.getTypeName(seq.getItemType()));
        handler.startElement(NAMESPACE, SEQ_ELEMENT, SEQ_ELEMENT_QNAME, attrs);
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            marshallItem(broker, i.nextItem(), handler);
        }
        handler.endElement(NAMESPACE, SEQ_ELEMENT, SEQ_ELEMENT_QNAME);
    }
    
    
    /**
     * Marshall the items of a sequence in  an xml based string representation.
     *
     * @param broker the database broker
     * @param seq Sequence which items are to be marshalled
     * @param start index of first item to be marshalled
     * @param howmany number of items following and including the first to be marshalled
     * @param handler Content handler for building the resulting string
     *
     * @throws XPathException if an XPath error occurs
     * @throws SAXException if a SAX parsing exception occurs
     */
    public static void marshall(final DBBroker broker, final Sequence seq, final int start, final int howmany,
            final ContentHandler handler) throws XPathException, SAXException {
        final AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", ATTR_ITEM_TYPE, ATTR_ITEM_TYPE, "CDATA", Type.getTypeName(seq.getItemType()));
        handler.startElement(NAMESPACE, SEQ_ELEMENT, SEQ_ELEMENT_QNAME, attrs);
        for (int i = start; i < howmany && i < seq.getItemCount(); i++ ) {
        	
            marshallItem(broker, seq.itemAt(i), handler);
        }
        handler.endElement(NAMESPACE, SEQ_ELEMENT, SEQ_ELEMENT_QNAME);
    }

    /**
     * Marshall an item in an xml based string representation.
     *
     * @param broker the database broker
     * @param item Sequence(or Item) to me marshalled
     * @param handler Content handler for building the resulting string
     *
     * @throws XPathException if an XPath error occurs
     * @throws SAXException if a SAX parsing exception occurs
     */
    public static void marshallItem(final DBBroker broker, final Item item, final ContentHandler handler)
            throws SAXException, XPathException {
        final AttributesImpl attrs = new AttributesImpl();
        int type = item.getType();
        if (type == Type.NODE) {
            final short nodeType = ((NodeValue)item).getNode().getNodeType();
            type = Type.fromDomNodeType(nodeType);
        }
        attrs.addAttribute("", ATTR_TYPE, ATTR_TYPE, "CDATA", Type.getTypeName(type));
        if (Type.subTypeOf(item.getType(), Type.NODE)) {
            handler.startElement(NAMESPACE, VALUE_ELEMENT, VALUE_ELEMENT_QNAME, attrs);
            final NodeValue nv = (NodeValue) item;
            nv.toSAX(broker, handler, OUTPUT_PROPERTIES);
            handler.endElement(NAMESPACE, VALUE_ELEMENT, VALUE_ELEMENT_QNAME);
        } else {
            handler.startElement(NAMESPACE, VALUE_ELEMENT, VALUE_ELEMENT_QNAME, attrs);
            final String value = item.getStringValue();
            handler.characters(value.toCharArray(), 0, value.length());
            handler.endElement(NAMESPACE, VALUE_ELEMENT, VALUE_ELEMENT_QNAME);
        }
    }

    public static Sequence demarshall(DBBroker broker, Reader reader) throws XMLStreamException, XPathException {
        final XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        final XMLStreamReader parser = factory.createXMLStreamReader(reader);
        return demarshall(broker, parser);
    }
    
    public static Sequence demarshall(DBBroker broker,Node n) throws XMLStreamException, XPathException {
    	final DOMSource source = new DOMSource(n, null);
    	final XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        
        final XMLStreamReader parser = factory.createXMLStreamReader(source);
    	return demarshall(broker,parser);    	
    	
    }

    public static Sequence demarshall(DBBroker broker, XMLStreamReader parser) throws XMLStreamException, XPathException {
        int event = parser.next();
        while (event != XMLStreamConstants.START_ELEMENT)
            event = parser.next();
        if (!NAMESPACE.equals(parser.getNamespaceURI()))
            {throw new XMLStreamException("Root element is not in the correct namespace. Expected: " + NAMESPACE);}
        if (!SEQ_ELEMENT.equals(parser.getLocalName()))
            {throw new XMLStreamException("Root element should be a " + SEQ_ELEMENT_QNAME);}
        final ValueSequence result = new ValueSequence();
        while ((event = parser.next()) != XMLStreamConstants.END_DOCUMENT) {
            switch (event) {
                case XMLStreamConstants.START_ELEMENT :
                    if (NAMESPACE.equals(parser.getNamespaceURI()) && VALUE_ELEMENT.equals(parser.getLocalName())) {
                        String typeName = null;
                        // scan through attributes instead of direct lookup to work around issue in xerces
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            if (ATTR_TYPE.equals(parser.getAttributeLocalName(i))) {
                                typeName = parser.getAttributeValue(i);
                                break;
                            }
                        }
                        if (typeName != null) {
                            final int type = Type.getType(typeName);
                            Item item;
                            if (Type.subTypeOf(type, Type.NODE))
                                {item = streamToDOM(type, parser, null);}
                            else
                                {item = new StringValue(null, parser.getElementText()).convertTo(type);}
                            result.add(item);
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT :
                    if (NAMESPACE.equals(parser.getNamespaceURI()) && SEQ_ELEMENT.equals(parser.getLocalName()))
                        {return result;}
                    break;
            }
        }
        return result;
    }

    public static Sequence demarshall(NodeImpl node) throws XMLStreamException, XPathException {
        final String ns = node.getNamespaceURI();
        if (ns == null || !NAMESPACE.equals(ns)) {
            throw new XMLStreamException("Root element is not in the correct namespace. Expected: " + NAMESPACE);
        }
        if (!SEQ_ELEMENT.equals(node.getLocalName()))
            {throw new XMLStreamException("Root element should be a " + SEQ_ELEMENT_QNAME);}
        final ValueSequence result = new ValueSequence();
        final InMemoryNodeSet values = new InMemoryNodeSet();
        node.selectChildren(new NameTest(Type.ELEMENT, VALUE_QNAME), values);
        for (final SequenceIterator i = values.iterate(); i.hasNext();) {
            final ElementImpl child = (ElementImpl) i.nextItem();
            final String typeName = child.getAttribute(ATTR_TYPE);
            if (typeName != null) {
                final int type = Type.getType(typeName);
                Item item;
                if (Type.subTypeOf(type, Type.NODE)) {
                    item = (Item) child.getFirstChild();
                    if (type == Type.DOCUMENT) {
                        final DocumentImpl n = (DocumentImpl) item;
                        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(n.getExpression());
                        try {
                            receiver.startDocument();
                            n.copyTo(n, receiver);
                            receiver.endDocument();
                        } catch (final SAXException e) {
                            throw new XPathException(item != null ? item.getExpression() : null, "Error while demarshalling node: " + e.getMessage(), e);
                        }
                        item = (Item) receiver.getDocument();
                    }
                } else {
                    final StringBuilder data = new StringBuilder();
                    Node txt = child.getFirstChild();
                    while (txt != null) {
                        if (!(txt.getNodeType() == Node.TEXT_NODE || txt.getNodeType() == Node.CDATA_SECTION_NODE)) {
                            throw new XMLStreamException("sx:value should only contain text if type is " + typeName);
                        }
                        data.append(txt.getNodeValue());
                        txt = txt.getNextSibling();
                    }
                    item = new StringValue(data.toString()).convertTo(type);
                }
                result.add(item);
            }
        }
        return result;
    }

    public static Item streamToDOM(XMLStreamReader parser, XQItemType type) throws XMLStreamException, XQException {
        if (type.getBaseType() == XQItemType.XQITEMKIND_DOCUMENT_ELEMENT ||
                type.getBaseType() == XQItemType.XQITEMKIND_DOCUMENT_SCHEMA_ELEMENT)
            {return streamToDOM(Type.DOCUMENT, parser, null);}
        else
            {return streamToDOM(Type.ELEMENT, parser, null);}
    }

    /**
     * Creates an Item from a streamed representation.
     *
     * @param rootType the type of the root node
     * @param parser Parser to read xml elements from
     * @return item the item
     *
     * @throws XMLStreamException if an error occurs during streaming.
     */
    public static Item streamToDOM(int rootType, XMLStreamReader parser) throws XMLStreamException {
        return streamToDOM(rootType, parser, null);
    }

    /**
     * Creates an Item from a streamed representation.
     *
     * @param rootType the type of the root node
     * @param parser Parser to read xml elements from
     * @param expression the expression from which the item derives
     * @return item the item
     *
     * @throws XMLStreamException if an error occurs during streaming.
     */
    public static Item streamToDOM(int rootType, XMLStreamReader parser, final Expression expression) throws XMLStreamException {
        final MemTreeBuilder builder = new MemTreeBuilder(expression);
        builder.startDocument();
        int event;
        boolean finish = false;
        while ((event = parser.next()) != XMLStreamConstants.END_DOCUMENT) {
            switch (event) {
                case XMLStreamConstants.START_ELEMENT :
                    final AttributesImpl attribs = new AttributesImpl();
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        final javax.xml.namespace.QName qn = parser.getAttributeName(i);
                        attribs.addAttribute(qn.getNamespaceURI(), qn.getLocalPart(), qn.getPrefix() + ':' + qn.getLocalPart(),
                                parser.getAttributeType(i), parser.getAttributeValue(i));
                    }
                   builder.startElement(QName.fromJavaQName(parser.getName()), attribs);
//                    for (int i = 0; i < parser.getNamespaceCount(); i++) {
//                        builder.namespaceNode(parser.getNamespacePrefix(i), parser.getNamespaceURI(i));
//                    }
                    break;
                case XMLStreamConstants.END_ELEMENT :
                    if (NAMESPACE.equals(parser.getNamespaceURI()) && VALUE_ELEMENT.equals(parser.getLocalName()))
                        {finish = true;}
                    else
                        {builder.endElement();}
                    break;
                case XMLStreamConstants.CHARACTERS :
                    builder.characters(parser.getText());
                    break;
            }
            if (finish) {break;}
        }
        builder.endDocument();
        if (rootType == Type.DOCUMENT)
            {return builder.getDocument();}
        else if (rootType == Type.ELEMENT)
            {return (NodeImpl) builder.getDocument().getDocumentElement();}
        else
            {return (NodeImpl) builder.getDocument().getFirstChild();}
    }
    
    
    
    /**
     * Creates an Item from a streamed representation.
     *
     * @param reader the reader.
     * @param type the type of the item.
     * @return item the result item
     *
     * @throws XMLStreamException if an error occurs during streaming.
     * @throws XQException if any other error occurs.
     */
    public static Item streamToDOM(Reader reader, XQItemType type) throws XMLStreamException, XQException {
    	final XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        final XMLStreamReader parser = factory.createXMLStreamReader(reader);
        
        return streamToDOM(parser, type);
    }
    
    
    
    /**
     * Creates a node from a string representation.
     *
     * @param content the content
     * @return node the result node.
     *
     * @throws XMLStreamException if an error occurs during streaming.
     */
    public static Node streamToNode(String content) throws XMLStreamException {
    	final StringReader reader = new StringReader(content);
    	return streamToNode(reader, null);
    }
    
    
   
    /**
     * Creates a node from a streamed representation.
     *
     * @param reader the reader.
     * @return item the result item.
     *
     * @throws XMLStreamException if an error occurs during streaming.
     */
    public static Node streamToNode(Reader reader) throws XMLStreamException {
        return streamToNode(reader, null);
    }
   
    /**
     * Creates a node from a streamed representation.
     *
     * @param reader the reader.
     * @param expression the expression from which te node derives
     * @return item the result item.
     *
     * @throws XMLStreamException if an error occurs during streaming.
     */
    public static Node streamToNode(Reader reader, final Expression expression) throws XMLStreamException {
    	final XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        final XMLStreamReader parser = factory.createXMLStreamReader(reader);
        final MemTreeBuilder builder = new MemTreeBuilder(expression);
        builder.startDocument();
        int event;
        boolean finish = false;
        while ((event = parser.next()) != XMLStreamConstants.END_DOCUMENT) {
            switch (event) {
                case XMLStreamConstants.START_ELEMENT :
                    final AttributesImpl attribs = new AttributesImpl();
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        final javax.xml.namespace.QName qn = parser.getAttributeName(i);
                        attribs.addAttribute(qn.getNamespaceURI(), qn.getLocalPart(), qn.getPrefix() + ':' + qn.getLocalPart(),
                                parser.getAttributeType(i), parser.getAttributeValue(i));
                    }
                   builder.startElement(QName.fromJavaQName(parser.getName()), attribs);
//                    for (int i = 0; i < parser.getNamespaceCount(); i++) {
//                        builder.namespaceNode(parser.getNamespacePrefix(i), parser.getNamespaceURI(i));
//                    }
                    break;
                case XMLStreamConstants.END_ELEMENT :
                    if (NAMESPACE.equals(parser.getNamespaceURI()) && VALUE_ELEMENT.equals(parser.getLocalName()))
                        {finish = true;}
                    else
                        {builder.endElement();}
                    break;
                case XMLStreamConstants.CHARACTERS :
                    builder.characters(parser.getText());
                    break;
            }
            if (finish) {break;}
        }
        builder.endDocument();
        return builder.getDocument().getDocumentElement();
    }
}



/*
 *  eXist EXPath
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.expath.httpclient.model.exist;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.ValueSequence;
import org.expath.httpclient.HttpClientException;
import org.expath.httpclient.HttpConstants;
import org.expath.httpclient.model.Attribute;
import org.expath.httpclient.model.Element;
import org.expath.httpclient.model.Sequence;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class EXistElement implements Element {

    private final NodeValue element;
    private final XQueryContext context;
    
    public EXistElement(NodeValue element, XQueryContext context) {
        this.element = element;
        this.context = context;
    }
    
    //@Override
    public Iterable<Attribute> attributes() throws HttpClientException {
        
        return new Iterable<Attribute>() {
            //@Override
            public Iterator<Attribute> iterator() {
                return new Iterator<Attribute>() {
                    
                    private final NamedNodeMap attrs = element.getNode().getAttributes();
                    private final int length = attrs.getLength();
                    private int position = 0;
                    
                    //@Override
                    public boolean hasNext() {
                        return(position < length);
                    }

                    //@Override
                    public Attribute next() {
                        if(position >= length){
                            throw new NoSuchElementException();
                        }
                        
                        return new EXistAttribute((Attr)attrs.item(position++));
                    }

                    //@Override
                    public void remove() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }
                    
                };
            }  
        };
    }

    //@Override
    public Iterable<Element> children() throws HttpClientException {
        final Node node = element.getNode();
        return new IterableElement(node);
    }

    //@Override
    public String getAttribute(String local_name) throws HttpClientException {
        String attrValue = null;
        NamedNodeMap attrs = element.getNode().getAttributes();
        Node attr = attrs.getNamedItem(local_name);
        if(attr != null) {
            attrValue = ((Attr)attr).getValue();
        }

        return attrValue;
    }

    //@Override
    public Sequence getContent() throws HttpClientException {
        org.exist.xquery.value.Sequence valueSequence = new ValueSequence();
        
        NodeList children = element.getNode().getChildNodes();
        
        try {
            for(int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                valueSequence.add((NodeValue)child);
            }
            return new EXistSequence(valueSequence, context);
        } catch(XPathException xpe) {
            throw new HttpClientException(xpe.getMessage(), xpe);
        }
    }

    //@Override
    public String getDisplayName() throws HttpClientException {
        return element.getNode().getNodeName();
    }

    //@Override
    public String getLocalName() throws HttpClientException {
        return element.getNode().getLocalName();
    }

    //@Override
    public String getNamespaceUri() {
        return element.getNode().getNamespaceURI();
    }

    //@Override
    public boolean hasNoNsChild() throws HttpClientException {
        NodeList children = element.getNode().getChildNodes();
        for(int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if(child.getNamespaceURI() == null && child.getPrefix() == null || child.getNamespaceURI().equalsIgnoreCase("")) {
                return true;
            }
        }
        return false;
    }

    //@Override
    public Iterable<Element> httpNsChildren() throws HttpClientException {
       
        final Node node = element.getNode();
        return new IterableElement(node, HttpConstants.HTTP_CLIENT_NS_URI);
    }

    /**
     * Check the element {@code elem} does not have attributes other than {@code names}.
     *
     * {@code names} contains non-qualified names, for allowed attributes.  The
     * element can have other attributes in other namespace (not in the HTTP
     * Client namespace) but no attributes in no namespace.
     *
     * @param names The non-qualified names of allowed attributes (cannot be
     * null, but can be empty.)
     *
     * @throws HttpClientException If the element contains an attribute in the
     * HTTP Client namespace, or in no namespace and the name of which is not
     * in {@code names}.
     */
    //@Override
    public void noOtherNCNameAttribute(String[] names) throws HttpClientException {
        NamedNodeMap attributes = element.getNode().getAttributes();
        
        for(int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if(attribute.getNamespaceURI() != null) {
                if(attribute.getNamespaceURI().equals(HttpConstants.HTTP_CLIENT_NS_URI)) {
                    throw new HttpClientException("Element contains an attribute '" + attribute.getNodeName() + "' in the HTTP Client namespace");
                }
            } else {
                boolean matched = false;
                for(String name: names) {
                    if(attribute.getLocalName().equals(name)) {
                        matched = true;
                        break;
                    }
                }
                if(!matched) {
                    throw new HttpClientException("Element contains an attribute '" + attribute.getNodeName() + "' in no namespace but that attribute is not permitted");
                }
            }
        }
    }
    
    public class IterableElement implements Iterable<Element> {

        private final Node node;
        private String inNamespaceURI = null;

        public IterableElement(Node node) {
            this.node = node;
        }
        
        public IterableElement(Node node, String inNamespaceURI) {
            this.node = node;
            this.inNamespaceURI = inNamespaceURI;
        }
        
        //@Override
        public Iterator<Element> iterator() {
            return new ElementIterator(node, inNamespaceURI);
        }
        
    }
    
    public class ElementIterator implements Iterator<Element> {
        
        private final Node parent;
        private final String inNamespaceURI;
        
        private List<org.w3c.dom.Element> elements = null;
        private int position = 0;
        
        public ElementIterator(Node parent, String inNamespaceURI) {
            this.parent = parent;
            this.inNamespaceURI = inNamespaceURI;
        }

        //@Override
        public boolean hasNext() {
            return(position < getLength());
        }

        //@Override
        public Element next() {
            if(position >= getLength()){
                throw new NoSuchElementException();
            }

            return new EXistElement((NodeValue)getElements().get(position++), context);
        }

        //@Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        private int getLength() {
            return getElements().size();
        }
        
        /**
         * lazy initialised
         */
        private List<org.w3c.dom.Element> getElements() {

            if(elements == null) {
                elements = new ArrayList<org.w3c.dom.Element>();
                NodeList children = parent.getChildNodes();

                for(int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if(child.getNodeType() == Node.ELEMENT_NODE) {
                        if(inNamespaceURI != null) {
                            if(inNamespaceURI.equals(child.getNamespaceURI())){
                                elements.add((org.w3c.dom.Element)child);
                            }
                        } else {
                            elements.add((org.w3c.dom.Element)child);
                        }
                    }
                }
            }
            
            return elements;
        }
    }
}
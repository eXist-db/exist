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
package org.expath.tools.model.exist;

import java.util.*;

import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.ValueSequence;
import org.expath.httpclient.HttpConstants;
import org.expath.tools.ToolsException;
import org.expath.tools.model.Attribute;
import org.expath.tools.model.Element;
import org.expath.tools.model.Sequence;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class EXistElement implements Element {

    private final NodeValue element;
    private final XQueryContext context;
    
    public EXistElement(final NodeValue element, final XQueryContext context) {
        this.element = element;
        this.context = context;
    }
    
    @Override
    public Iterable<Attribute> attributes() {
        
        return new Iterable<Attribute>() {
            @Override
            public Iterator<Attribute> iterator() {
                return new Iterator<Attribute>() {
                    
                    private final NamedNodeMap attrs = element.getNode().getAttributes();
                    private final int length = attrs.getLength();
                    private int position = 0;
                    
                    @Override
                    public boolean hasNext() {
                        return(position < length);
                    }

                    @Override
                    public Attribute next() {
                        if(position >= length){
                            throw new NoSuchElementException();
                        }
                        
                        return new EXistAttribute((Attr)attrs.item(position++));
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }
                    
                };
            }  
        };
    }

    @Override
    public Iterable<Element> children() {
        final Node node = element.getNode();
        return new IterableElement(node);
    }

    @Override
    public String getAttribute(final String local_name) {
        String attrValue = null;
        final NamedNodeMap attrs = element.getNode().getAttributes();
        final Node attr = attrs.getNamedItem(local_name);
        if(attr != null) {
            attrValue = ((Attr)attr).getValue();
        }

        return attrValue;
    }

    @Override
    public Sequence getContent() {
        final org.exist.xquery.value.Sequence valueSequence = new ValueSequence();
        
        final NodeList children = element.getNode().getChildNodes();
        
        try {
            for(int i = 0; i < children.getLength(); i++) {
                final Node child = children.item(i);
                valueSequence.add((NodeValue)child);
            }
            return new EXistSequence(valueSequence, context);
        } catch(final XPathException xpe) {
            throw new RuntimeException(xpe.getMessage(), xpe);
        }
    }

    @Override
    public String getDisplayName() {
        return element.getNode().getNodeName();
    }

    @Override
    public String getLocalName() {
        return element.getNode().getLocalName();
    }

    @Override
    public String getNamespaceUri() {
        return element.getNode().getNamespaceURI();
    }

    @Override
    public boolean hasNoNsChild() {
        final NodeList children = element.getNode().getChildNodes();
        for(int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if(child.getNamespaceURI() == null && child.getPrefix() == null || child.getNamespaceURI().equals(XMLConstants.NULL_NS_URI)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterable<Element> children(final String ns) {
        final Node node = element.getNode();
        return new IterableElement(node, ns);
    }

    @Override
    public void noOtherNCNameAttribute(final String[] names, String[] forbidden_ns) throws ToolsException {
        if ( forbidden_ns == null ) {
            forbidden_ns = new String[] { };
        }

        final String[] sorted_names = sortCopy(names);
        final String[] sorted_ns = sortCopy(forbidden_ns);

        final NamedNodeMap attributes = element.getNode().getAttributes();
        
        for(int i = 0; i < attributes.getLength(); i++) {
            final Node attr = attributes.item(i);
            final String attr_name = attr.getLocalName();
            final String ns = attr.getNamespaceURI();

            if( ns != null && Arrays.binarySearch(sorted_ns, ns) >= 0 ) {
                if(ns.equals(HttpConstants.HTTP_CLIENT_NS_URI)) {
                    throw new ToolsException("@" + attr_name + " in namespace " + ns + " not allowed on " + getDisplayName());
                }
            } else if (ns!= null && ! ns.isEmpty() ) {
                // ignore other-namespace-attributes
            } else if ( Arrays.binarySearch(sorted_names, attr.getLocalName()) < 0 ) {
                throw new ToolsException("@" + attr_name + " not allowed on " + getDisplayName());
            }
        }
    }

    private String[] sortCopy(final String[] array)
    {
        final String[] sorted = new String[array.length];
        System.arraycopy(array, 0, sorted, 0, sorted.length);
        Arrays.sort(sorted);
        return sorted;
    }

    @Override
    public QName parseQName(final String value)
            throws ToolsException
    {
        try {
            final org.exist.dom.QName qn = org.exist.dom.QName.parse(context, value, element.getQName().getNamespaceURI());
            return qn.toJavaQName();
        }
        catch ( final org.exist.dom.QName.IllegalQNameException ex ) {
            throw new ToolsException("Error parsing the literal QName: " + value, ex);
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
        
        @Override
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

        @Override
        public boolean hasNext() {
            return(position < getLength());
        }

        @Override
        public Element next() {
            if(position >= getLength()){
                throw new NoSuchElementException();
            }

            return new EXistElement((NodeValue)getElements().get(position++), context);
        }

        @Override
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
                elements = new ArrayList<>();
                final NodeList children = parent.getChildNodes();

                for(int i = 0; i < children.getLength(); i++) {
                    final Node child = children.item(i);
                    if(child.getNodeType() == Node.ELEMENT_NODE) {
                        if(inNamespaceURI != null) {
                            final String ns = child.getNamespaceURI();
                            if(ns != null && inNamespaceURI.equals(ns)){
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
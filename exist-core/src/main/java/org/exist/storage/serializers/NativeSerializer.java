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
package org.exist.storage.serializers;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.CDATASectionImpl;
import org.exist.dom.persistent.CommentImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.ProcessingInstructionImpl;
import org.exist.dom.persistent.TextImpl;
import org.exist.dom.persistent.XMLDeclarationImpl;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.serializer.AttrList;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.exist.storage.dom.INodeIterator;

import javax.xml.XMLConstants;

/**
 * Serializer implementation for the native database backend.
 * 
 * @author wolf
 */
public class NativeSerializer extends Serializer {

    // private final static AttributesImpl EMPTY_ATTRIBUTES = new AttributesImpl();
    
    private final static QName TEXT_ELEMENT = new QName("text", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);
    private final static QName ATTRIB_ELEMENT = new QName("attribute", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);
    private final static QName SOURCE_ATTRIB = new QName("source", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);
    private final static QName ID_ATTRIB = new QName("id", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);

    private final static QName MATCHES_ATTRIB = new QName("matches", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);
    private final static QName MATCHES_OFFSET_ATTRIB = new QName("matches-offset", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);
    private final static QName MATCHES_LENGTH_ATTRIB = new QName("matches-length", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);

    private final static Pattern P_ZERO_VALUES = Pattern.compile("0(,0)?");
    private final static Matcher M_ZERO_VALUES = P_ZERO_VALUES.matcher("");

    public NativeSerializer(DBBroker broker, Configuration config) {
        this(broker, config, null);
    }

    public NativeSerializer(DBBroker broker, Configuration config, List<String> chainOfReceivers) {
        super(broker, config, chainOfReceivers);
    }

    protected void serializeToReceiver(NodeProxy p, boolean generateDocEvent, boolean checkAttributes)
    throws SAXException {
    	if(Type.subTypeOf(p.getType(), Type.DOCUMENT) || p.getNodeId() == NodeId.DOCUMENT_NODE) {
    			serializeToReceiver(p.getOwnerDocument(), generateDocEvent);
    			return;
    	}
    	setDocument(p.getOwnerDocument());
    	if (generateDocEvent && !documentStarted) {
            receiver.startDocument();
            documentStarted = true;
        }

        try(final INodeIterator domIter = broker.getNodeIterator(p)) {
            serializeToReceiver(null, domIter, p.getOwnerDocument(), checkAttributes, p.getMatches(), new TreeSet<>());
        } catch(final IOException e) {
            LOG.warn("Unable to close node iterator", e);
        }

        if(generateDocEvent) {
            receiver.endDocument();
        }
    }

    protected void serializeToReceiver(DocumentImpl doc, boolean generateDocEvent) throws SAXException {
        final long start = System.currentTimeMillis();

        setDocument(doc);
        final NodeList children = doc.getChildNodes();
        if (generateDocEvent && !documentStarted) {
            receiver.startDocument();
            documentStarted = true;
        }

        if (doc.getXmlDeclaration() != null){
            if ("no".equals(getProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "no"))) {
                final XMLDeclarationImpl xmlDecl = doc.getXmlDeclaration();
                receiver.declaration(xmlDecl.getVersion(), xmlDecl.getEncoding(), xmlDecl.getStandalone());
            }
        }

        if (doc.getDoctype() != null) {
            if ("yes".equals(getProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "no"))) {
                final DocumentTypeImpl docType = (DocumentTypeImpl)doc.getDoctype();
                serializeToReceiver(docType, null, docType.getOwnerDocument(), true, null, new TreeSet<>());
            }
        }

        // iterate through children
        for (int i = 0; i < children.getLength(); i++) {
            final IStoredNode<?> node = (IStoredNode<?>) children.item(i);
            try(final INodeIterator domIter = broker.getNodeIterator(node)) {
                domIter.next();
                final NodeProxy p = new NodeProxy(null, node);
                serializeToReceiver(node, domIter, (DocumentImpl) node.getOwnerDocument(),
                        true, p.getMatches(), new TreeSet<>());
            } catch(final IOException ioe) {
                LOG.warn("Unable to close node iterator", ioe);
            }
        }

        if (generateDocEvent) {receiver.endDocument();}

        if (LOG.isDebugEnabled())
        {
            LOG.debug("serializing document {} ({}) to SAX took {} msec", doc.getDocId(), doc.getURI(), System.currentTimeMillis() - start);}

    }
    
    
    protected void serializeToReceiver(IStoredNode node, INodeIterator iter,
            DocumentImpl doc, boolean first, Match match, Set<String> namespaces) throws SAXException {
        if (node == null && iter.hasNext()) {
            node = iter.next();
        }
        if (node == null) {
            return;
        }
        // char ch[];
        String cdata;
        switch (node.getNodeType()) {
        case Node.ELEMENT_NODE:
            receiver.setCurrentNode(node);
        	String defaultNS = null;
	        if (((ElementImpl) node).declaresNamespacePrefixes()) {
	        	// declare namespaces used by this element
	        	String prefix, uri;
	        	for (final Iterator<String> i = ((ElementImpl) node).getPrefixes(); i.hasNext();) {
	        		prefix = i.next();
	        		if (prefix.isEmpty()) {
	        			defaultNS = ((ElementImpl) node).getNamespaceForPrefix(prefix);
	        			receiver.startPrefixMapping(XMLConstants.DEFAULT_NS_PREFIX, defaultNS);
	        			namespaces.add(defaultNS);
	        		} else {
	        			uri = ((ElementImpl) node).getNamespaceForPrefix(prefix);
	        			receiver.startPrefixMapping(prefix, uri);
	        			namespaces.add(uri);
	        		}
	        	}
	        }
	        final String ns = defaultNS == null ? node.getNamespaceURI() : defaultNS;
	        if (ns != null && !ns.isEmpty() && (!namespaces.contains(ns))) {
                String prefix = node.getPrefix();
                if(prefix == null) {
                    prefix = XMLConstants.DEFAULT_NS_PREFIX;
                }
	            receiver.startPrefixMapping(prefix, ns);
	        }
        	final AttrList attribs = new AttrList();
        	if ((first && showId == EXIST_ID_ELEMENT) || showId == EXIST_ID_ALL) {
                attribs.addAttribute(ID_ATTRIB, node.getNodeId().toString());
            /* 
             * This is a proposed fix-up that the serializer could do
             * to make sure elements always have the namespace declarations
             *
            } else {
               // This is fix-up for when the node has a namespace but there is no
               // namespace declaration.
               String elementNS = node.getNamespaceURI();
               Node parent = node.getParentNode();
               if (parent instanceof ElementImpl) {
                  ElementImpl parentElement = (ElementImpl)parent;
                  String declaredNS = parentElement.getNamespaceForPrefix(node.getPrefix());
                  if (elementNS!=null && declaredNS==null) {
                     // We need to declare the prefix as it was missed somehow
                     receiver.startPrefixMapping(node.getPrefix(), elementNS);
                  } else if (elementNS==null && declaredNS!=null) {
                     // We need to declare the default namespace to be the no namespace
                     receiver.startPrefixMapping(node.getPrefix(), elementNS);
                  } else if (!elementNS.equals(defaultNS)) {
                     // Same prefix but different namespace
                     receiver.startPrefixMapping(node.getPrefix(), elementNS);
                  }
               } else if (elementNS!=null) {
                  // If the parent is the document, we must have a namespace
                  // declaration when there is a namespace URI.
                  receiver.startPrefixMapping(node.getPrefix(), elementNS);
               }
             */
            }
            if (first && showId > 0) {
            	// String src = doc.getCollection().getName() + "/" + doc.getFileName();
                attribs.addAttribute(SOURCE_ATTRIB, doc.getFileURI().toString());
            }
            final int children = node.getChildCount();
            int count = 0;
            IStoredNode child = null;
            StringBuilder matchAttrCdata = null;
            StringBuilder matchAttrOffsetsCdata = null;
            StringBuilder matchAttrLengthsCdata = null;
            while (count < children) {
                child = iter.hasNext() ? iter.next() : null;
                if (child != null && child.getNodeType() == Node.ATTRIBUTE_NODE) {

                    if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES)  == TAG_ATTRIBUTE_MATCHES && match != null && child.getNodeId().equals(match.getNodeId())) {
                        if(matchAttrCdata == null) {
                            matchAttrCdata = new StringBuilder();
                            matchAttrOffsetsCdata = new StringBuilder();
                            matchAttrLengthsCdata = new StringBuilder();
                        } else {
                            matchAttrCdata.append(",");
                            matchAttrOffsetsCdata.append(",");
                            matchAttrLengthsCdata.append(",");
                        }
                        matchAttrCdata.append(child.getQName().toString());
                        matchAttrOffsetsCdata.append(match.getOffset(0).getOffset());
                        matchAttrLengthsCdata.append(match.getOffset(0).getLength());

                        match = match.getNextMatch();
                    }

                    cdata = ((AttrImpl) child).getValue();
                    attribs.addAttribute(child.getQName(), cdata);
                    count++;
                    child.release();
                } else {
                    break;
                }
            }
            if(matchAttrCdata != null) {
                attribs.addAttribute(MATCHES_ATTRIB, matchAttrCdata.toString());

                //mask the full-text index which doesn't provide offset and length
                M_ZERO_VALUES.reset(matchAttrOffsetsCdata);
                final boolean offsetsIsZero = M_ZERO_VALUES.matches();
                M_ZERO_VALUES.reset(matchAttrLengthsCdata);
                final boolean lengthsIsZero = M_ZERO_VALUES.matches();

                if(!offsetsIsZero && !lengthsIsZero) {
                    attribs.addAttribute(MATCHES_OFFSET_ATTRIB, matchAttrOffsetsCdata.toString());
                    attribs.addAttribute(MATCHES_LENGTH_ATTRIB, matchAttrLengthsCdata.toString());
                }
            }

            receiver.setCurrentNode(node);
            receiver.startElement(node.getQName(), attribs);
            while (count < children) {
                serializeToReceiver(child, iter, doc, false, match, namespaces);
                if (++count < children) {
                    child = iter.hasNext() ? iter.next() : null;
                } else
                    {break;}
            }
            receiver.setCurrentNode(node);
            receiver.endElement(node.getQName());
            if (((ElementImpl) node).declaresNamespacePrefixes()) {
                for (final Iterator<String> i = ((ElementImpl) node).getPrefixes(); i.hasNext();) {
                    final String prefix = i.next();
                    receiver.endPrefixMapping(prefix);
                }
            }
            if (ns != null && !ns.isEmpty() && (!namespaces.contains(ns))) {
                String prefix = node.getPrefix();
                if(prefix == null) {
                    prefix = XMLConstants.DEFAULT_NS_PREFIX;
                }
                receiver.endPrefixMapping(prefix);
            }
            node.release();
            break;
        case Node.TEXT_NODE:
        	if (first && createContainerElements) {
                final AttrList tattribs = new AttrList();
                if (showId > 0) {
                    tattribs.addAttribute(ID_ATTRIB, node.getNodeId().toString());
                    tattribs.addAttribute(SOURCE_ATTRIB, doc.getFileURI().toString());
                }
                receiver.startElement(TEXT_ELEMENT, tattribs);
            }
            receiver.setCurrentNode(node);
            receiver.characters(((TextImpl) node).getXMLString());
            if (first && createContainerElements)
                {receiver.endElement(TEXT_ELEMENT);}
            node.release();
            break;
        case Node.ATTRIBUTE_NODE:
            if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES)  == TAG_ATTRIBUTE_MATCHES && match != null && node.getNodeId().equals(match.getNodeId())) {
                //TODO(AR) do we need to expand attribute matches here also? see {@code matchAttrCdata} above
            }

            cdata = ((AttrImpl) node).getValue();
        	if(first) {
                if (createContainerElements) {               
            		final AttrList tattribs = new AttrList();
                    if (showId > 0) {
                        tattribs.addAttribute(ID_ATTRIB, node.getNodeId().toString());
                        tattribs.addAttribute(SOURCE_ATTRIB, doc.getFileURI().toString());
                    }
                    tattribs.addAttribute(node.getQName(), cdata);
                    receiver.startElement(ATTRIB_ELEMENT, tattribs);
                    receiver.endElement(ATTRIB_ELEMENT);
                }
                else {
                	if (this.outputProperties.getProperty("output-method") != null &&
                			"text".equals(this.outputProperties.getProperty("output-method"))) {
                		receiver.characters(node.getNodeValue());                	
                	} else {
                        LOG.warn("Error SENR0001: attribute '{}' has no parent element. While serializing document {}", node.getQName(), doc.getURI());
                		throw new SAXException("Error SENR0001: attribute '" + node.getQName() + "' has no parent element");
                	}
                }
            } else
        		{receiver.attribute(node.getQName(), cdata);}
            node.release();
            break;
		case Node.DOCUMENT_TYPE_NODE:
			final String systemId = ((DocumentTypeImpl) node).getSystemId();
			final String publicId =  ((DocumentTypeImpl) node).getPublicId();
			final String name = ((DocumentTypeImpl) node).getName();
			receiver.documentType(name, publicId, systemId);
			break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            receiver.processingInstruction(
                    ((ProcessingInstructionImpl) node).getTarget(),
                    ((ProcessingInstructionImpl) node).getData());
            node.release();
            break;
        case Node.COMMENT_NODE:
            final String comment = ((CommentImpl) node).getData();
            char data[] = new char[comment.length()];
            comment.getChars(0, data.length, data, 0);
            receiver.comment(data, 0, data.length);
            node.release();
            break;
        case Node.CDATA_SECTION_NODE:
            final String str = ((CDATASectionImpl)node).getData();
            if (first)
                {receiver.characters(str);}
            else {
                data = new char[str.length()];
                str.getChars(0,str.length(), data, 0);   
                receiver.cdataSection(data, 0, data.length);
            }
            break;
        //TODO : how to process other types ? -pb
        }
    }
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  $Id$
 */
package org.exist.contentextraction.xquery;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.log4j.Logger;
import org.exist.contentextraction.ContentExtraction;
import org.exist.contentextraction.ContentExtractionException;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.NodePath;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author Dulip Withanage <dulip.withanage@gmail.com>
 * @version 1.0
 */
public class ContentFunctions extends BasicFunction {

    @SuppressWarnings("unused")
    private final static Logger logger = Logger.getLogger(ContentFunctions.class);

    public final static FunctionSignature getMeatadata = new FunctionSignature(
        new QName("get-metadata", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted metadata")
    );

    public final static FunctionSignature getMetadataAndContent = new FunctionSignature(
        new QName("get-metadata-and-content", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata and contents",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted content and metadata")
    );

    public final static FunctionSignature streamContent = new FunctionSignature(
        new QName("stream-content", ContentExtractionModule.NAMESPACE_URI, ContentExtractionModule.PREFIX),
        "extracts the metadata",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data to extract from"),
            new FunctionParameterSequenceType("paths", Type.STRING, Cardinality.ZERO_OR_MORE, 
            		"A sequence of (simple) node paths which should be passed to the callback function"),
            new FunctionParameterSequenceType("callback", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, 
            		"The callback function. Expected signature: " +
					"callback($node as node(), $userData as item()*, $retValue as item()*)," +
					"where $node is the currently processed node, $userData contains the data supplied in the " +
					"$userData parameter of stream-content, and $retValue is the return value of the previous " +
					"call to the callback function. The last two parameters are used for passing information " +
					"between the calling function and subsequent invocations of the callback function."),
            new FunctionParameterSequenceType("namespaces", Type.ELEMENT, Cardinality.ZERO_OR_ONE,
            		"Prefix/namespace mappings to be used for matching the paths. Pass an XML fragment with the following " +
            		"structure: <namespaces><namespace prefix=\"prefix\" uri=\"uri\"/></namespaces>."),
            new FunctionParameterSequenceType("userData", Type.ITEM, Cardinality.ZERO_OR_MORE,
            		"Additional data which will be passed to the callback function.")
        },
        new FunctionReturnSequenceType(Type.EMPTY, Cardinality.EMPTY, "Returns empty sequence")
    );
    
    public ContentFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // is argument the empty sequence?
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        DocumentBuilderReceiver builder = new DocumentBuilderReceiver();
        ContentExtraction ce = new ContentExtraction();

        if (isCalledAs("stream-content")) {
            
            /* binary content */
            BinaryValue binary = (BinaryValue) args[0].itemAt(0);
            
            /* callback function */
            FunctionReference ref = (FunctionReference) args[2].itemAt(0);
            
            Map<String, String> mappings = new HashMap<String, String>();         
            if (args[3].hasOne()) {
                NodeValue namespaces = (NodeValue) args[3].itemAt(0);
                parseMappings(namespaces, mappings);
            }
            
            return streamContent(ce, binary, args[1], ref, mappings, args[4]);
            
        } else {
            
            try {
                if (isCalledAs("get-metadata")) {
                    ce.extractMetadata((BinaryValue) args[0].itemAt(0), (ContentHandler) builder);
                    
                } else {
                    ce.extractContentAndMetadata((BinaryValue) args[0].itemAt(0), (ContentHandler) builder);
                }

                return (NodeValue) builder.getDocument();
                
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new XPathException(ex.getMessage(), ex);
                
            } catch (SAXException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new XPathException(ex.getMessage(), ex);
                
            } catch (ContentExtractionException ex) {
                LOG.error(ex.getMessage(), ex);
                throw new XPathException(ex.getMessage(), ex);
            }
        }
    }

    
    private void parseMappings(NodeValue namespaces, Map<String, String> mappings) throws XPathException {
        
        try {
            XMLStreamReader reader = context.getXMLStreamReader(namespaces);
            reader.next();
            
            while (reader.hasNext()) {
                
                int status = reader.next();
                if (status == XMLStreamReader.START_ELEMENT && reader.getLocalName().equals("namespace")) {
                    String prefix = reader.getAttributeValue("", "prefix");
                    String uri = reader.getAttributeValue("", "uri");
                    mappings.put(prefix, uri);
                }
            }
            
        } catch (XMLStreamException e) {
            throw new XPathException(this, "Error while parsing namespace mappings: " + e.getMessage(), e);
            
        } catch (IOException e) {
            throw new XPathException(this, "Error while parsing namespace mappings: " + e.getMessage(), e);
        }
    }

    
    private Sequence streamContent(ContentExtraction ce, BinaryValue binary, Sequence pathSeq,
            FunctionReference ref, Map<String, String> mappings, Sequence data) throws XPathException {
        
        NodePath[] paths = new NodePath[pathSeq.getItemCount()];
        int i = 0;
        
        for (SequenceIterator iter = pathSeq.iterate(); iter.hasNext(); i++) {
            String path = iter.nextItem().getStringValue();
            paths[i] = new NodePath(mappings, path, false);
        }

        ContentReceiver receiver = new ContentReceiver(paths, ref, data);
        
        try {
            ce.extractContentAndMetadata(binary, receiver);
            
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new XPathException(ex.getMessage(), ex);
            
        } catch (SAXException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new XPathException(ex.getMessage(), ex);
            
        } catch (ContentExtractionException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new XPathException(ex.getMessage(), ex);
        }
        
        return receiver.getResult();
    }
	
    
    private class ContentReceiver implements Receiver {

        private ValueSequence result = new ValueSequence();
        private FunctionReference ref;
        private NodePath currentPath = new NodePath();
        private NodePath[] paths;
        private DocumentBuilderReceiver docBuilderReceiver = null;
        private NodePath lastPath = null;
        private Sequence userData = null;
        private Sequence prevReturnData = Sequence.EMPTY_SEQUENCE;

        ContentReceiver(NodePath[] paths, FunctionReference ref, Sequence userData) {
            this.paths = paths;
            this.ref = ref;
            this.userData = userData;
        }

        protected Sequence getResult() {
            return result;
        }

        /**
         *  Check if content of current (node) path should be retrieved.
         * 
         * @param path Xpath to current node
         * 
         * @return TRUE if path is in to-be-retrieved paths
         */
        private boolean matches(NodePath path) {
            for (int i = 0; i < paths.length; i++) {
                if (paths[i].match(path)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void startDocument() throws SAXException {
        }

        @Override
        public void endDocument() throws SAXException {
        }

        @Override
        public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        }

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {
        }

        @Override
        public void startElement(QName qname, AttrList attribs) throws SAXException {
            
            currentPath.addComponent(qname);
            
            // if current xpath is one of required paths
            if (matches(currentPath) && docBuilderReceiver == null) {
                lastPath = currentPath;
                context.pushDocumentContext();
                MemTreeBuilder memBuilder = context.getDocumentBuilder();
                docBuilderReceiver = new DocumentBuilderReceiver(memBuilder);
            }

            // Create element in result
            if (docBuilderReceiver != null) {
                docBuilderReceiver.startElement(qname, attribs);
            }
        }

        @Override
        public void endElement(QName qname) throws SAXException {
            
            if (docBuilderReceiver != null) {

                // Add end element
                docBuilderReceiver.endElement(qname);

                // If path was to be matched path
                if (currentPath.match(lastPath)) {
                    
                    // Retrieve result in mem document
                    Document doc = docBuilderReceiver.getDocument();
                    
                    // Get the root
                    NodeImpl root = (NodeImpl) doc.getDocumentElement();

                    docBuilderReceiver = null;
                    lastPath = null;
                    context.popDocumentContext();

                    // Construct parameters
                    Sequence[] params = new Sequence[3];
                    params[0] = root;
                    params[1] = userData;
                    params[2] = prevReturnData;

                    try {
                        // Send data to callback function
                        Sequence ret = ref.evalFunction(null, null, params);
                        prevReturnData = ret;
                        result.addAll(ret);

                    } catch (XPathException e) {
                        LOG.warn(e.getMessage(), e);
                    }
                }
            }
            
            // reduce xpath
            currentPath.removeLastComponent();
        }

        @Override
        public void characters(CharSequence seq) throws SAXException {
            
            // DW: receiver is null for subsequent <p> elements.
            // Need to figure out about the design of class
            
            if (docBuilderReceiver != null) {
                docBuilderReceiver.characters(seq);
            }
        }

        @Override
        public void attribute(QName qname, String value) throws SAXException {
            if (docBuilderReceiver != null) {
                docBuilderReceiver.attribute(qname, value);
            }
        }

        @Override
        public void comment(char[] ch, int start, int length) throws SAXException {
        }

        @Override
        public void cdataSection(char[] ch, int start, int len) throws SAXException {
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
        }

        @Override
        public void documentType(String name, String publicId, String systemId)  throws SAXException {
        }

        @Override
        public void highlightText(CharSequence seq) throws SAXException {
        }

        @Override
        public void setCurrentNode(StoredNode node) {
        }

        @Override
        public Document getDocument() {
            return null;
        }
    }
}

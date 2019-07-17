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
package org.exist.contentextraction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:dulip.withanage@gmail.com">Dulip Withanage</a>
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 * 
 * @version 1.1
 */
public class ContentReceiver implements Receiver {

    private final static Logger LOG = LogManager.getLogger(ContentReceiver.class);
    
    private final ValueSequence result = new ValueSequence();
    private final FunctionReference ref;
    private final NodePath currentElementPath = new NodePath();
    private final NodePath[] paths;
    private DocumentBuilderReceiver docBuilderReceiver = null;
    private NodePath startElementPath = null;
    private Sequence userData = null;
    private Sequence prevReturnData = Sequence.EMPTY_SEQUENCE;
    private final XQueryContext context;
    
    private boolean sendDataToCB = false;

    /**
     *  Receiver constructor
     * 
     * @param context The XQuery context
     * @param paths   Paths that must be extracted from the TIKA XHTML document
     * @param ref     Reference to callback function
     * @param userData Additional user supplied datas
     */
    public ContentReceiver(XQueryContext context, NodePath[] paths, FunctionReference ref, Sequence userData) {
        this.context = context;
        this.paths = paths;
        this.ref = ref;
        this.userData = userData;
    }

    /**
     * Get the result of the content extraction.
     * 
     * @return the result sequence.
     */
    public Sequence getResult() {
        return result;
    }

    /**
     * Check if content of current (node) path should be retrieved.
     *
     * @param path Xpath to current node
     *
     * @return TRUE if path is in to-be-retrieved paths
     */
    private boolean matches(NodePath path) {
        for (NodePath p : paths) {
            if (p.match(path)) {
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
        
        // Calculate path to current element
        currentElementPath.addComponent(qname);
        
        // Current path matches wanted path
        if ( matches(currentElementPath) ){
            
            if(sendDataToCB) {
                // Data is already sent to callback, ignore

            } else {
                // New element match, new data
                
                // Save reference to current path 
                startElementPath = new NodePath(currentElementPath);
                
                // Store old fragment in stack
                context.pushDocumentContext();
                
                // Create new receiver
                MemTreeBuilder memBuilder = context.getDocumentBuilder();
                docBuilderReceiver = new DocumentBuilderReceiver(memBuilder);
                
                // Switch on retrievel
                sendDataToCB=true;
            }       
        }
        
        if(sendDataToCB){
            docBuilderReceiver.startElement(qname, attribs);
        }
        
    }

    @Override
    public void endElement(QName qname) throws SAXException {
        
        // Send end element to result
        if(sendDataToCB){
            docBuilderReceiver.endElement(qname);
        }    
        
        // If path was to be matched path
        if (sendDataToCB && currentElementPath.match(startElementPath)) {
                        
            // flush the collected data
            sendDataToCallback();
            
            // get back from stack
            context.popDocumentContext();
        
            // Switch off retrieval
            sendDataToCB=false;
            docBuilderReceiver = null; 
        }
        
        // calculate new path
        currentElementPath.removeLastComponent();
        
    }

    @Override
    public void characters(CharSequence seq) throws SAXException {

        if (sendDataToCB) {
            docBuilderReceiver.characters(seq);
        } 
    }

    @Override
    public void attribute(QName qname, String value) throws SAXException {
        if (sendDataToCB) {
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
    public void documentType(String name, String publicId, String systemId) throws SAXException {
    }

    @Override
    public void highlightText(CharSequence seq) throws SAXException {
    }

    @Override
    public void setCurrentNode(INodeHandle node) {
    }

    /**
     * Does not return anything.
     * 
     * @return NULL
     */
    @Override
    public Document getDocument() {
        return null;
    }

    /**
     * Send data to callback handler
     */
    private void sendDataToCallback() {
        // Retrieve result as document
        Document doc = docBuilderReceiver.getDocument();

        // Get the root
        NodeImpl root = (NodeImpl) doc.getDocumentElement();             

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

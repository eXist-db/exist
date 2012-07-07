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

import org.apache.log4j.Logger;
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
import org.xml.sax.SAXException;

/**
 * @author Dulip Withanage <dulip.withanage@gmail.com>
 * @version 1.0
 */
public class ContentReceiver implements Receiver {

    private final static Logger LOG = Logger.getLogger(ContentReceiver.class);
    
    private ValueSequence result = new ValueSequence();
    private FunctionReference ref;
    private NodePath currentElementPath = new NodePath();
    private NodePath[] paths;
    private DocumentBuilderReceiver docBuilderReceiver = null;
    private NodePath startElementPath = null;
    private Sequence userData = null;
    private Sequence prevReturnData = Sequence.EMPTY_SEQUENCE;
    private XQueryContext context;

    public ContentReceiver(XQueryContext context, NodePath[] paths, FunctionReference ref, Sequence userData) {
        this.context = context;
        this.paths = paths;
        this.ref = ref;
        this.userData = userData;
    }

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

        currentElementPath.addComponent(qname);

        // if current path is one of required paths
        // and there is a docReceiver (=data must be collected)
        if (matches(currentElementPath) && docBuilderReceiver == null) {
            startElementPath = currentElementPath;
            context.pushDocumentContext();
            MemTreeBuilder memBuilder = context.getDocumentBuilder();
            docBuilderReceiver = new DocumentBuilderReceiver(memBuilder);
        }

        // Add element to result
        if (docBuilderReceiver != null) {
            docBuilderReceiver.startElement(qname, attribs);
        }
    }

    @Override
    public void endElement(QName qname) throws SAXException {

        // not null means data must be collecterd
        if (docBuilderReceiver != null) {

            // Add end element
            docBuilderReceiver.endElement(qname);

            // If path was to be matched path
            if (currentElementPath.match(startElementPath)) {

                // Retrieve result as document
                Document doc = docBuilderReceiver.getDocument();

                // Get the root
                NodeImpl root = (NodeImpl) doc.getDocumentElement();

                docBuilderReceiver = null;
                startElementPath = null;
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

        // reduce path
        // DW: should be earlier? currentElementPath is one level wrong
        currentElementPath.removeLastComponent();
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
    public void documentType(String name, String publicId, String systemId) throws SAXException {
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

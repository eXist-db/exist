/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2008-2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.config;

import org.exist.Database;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.NodeAtExist;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.*;

/**
 * Document proxy object. Help to provide single interface for in-memory & store documents.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ProxyDocument<E extends DocumentAtExist> extends ProxyNode<E>
        implements DocumentAtExist {

    private E document;

    public Database getDatabase() {
        return getProxyObject().getDatabase();
    }

    /* (non-Javadoc)
     * @see org.exist.i.dom.DocumentAtExist#getFirstChildFor(int)
     */
    public int getFirstChildFor(int nodeNumber) {
        return getProxyObject().getFirstChildFor(nodeNumber);
    }

    /* (non-Javadoc)
     * @see org.exist.i.dom.DocumentAtExist#getNextNodeNumber(int)
     */
    public int getNextNodeNumber(int nodeNr) throws DOMException {
        return getProxyObject().getNextNodeNumber(nodeNr);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#adoptNode(org.w3c.dom.Node)
     */
    public Node adoptNode(Node source) throws DOMException {
        return getProxyObject().adoptNode(source);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createAttribute(java.lang.String)
     */
    public Attr createAttribute(String name) throws DOMException {
        return getProxyObject().createAttribute(name);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createAttributeNS(java.lang.String, java.lang.String)
     */
    public Attr createAttributeNS(String namespaceURI, String qualifiedName)
            throws DOMException {
        return getProxyObject().createAttributeNS(namespaceURI, qualifiedName);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createCDATASection(java.lang.String)
     */
    public CDATASection createCDATASection(String data) throws DOMException {
        return getProxyObject().createCDATASection(data);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createComment(java.lang.String)
     */
    public Comment createComment(String data) {
        return getProxyObject().createComment(data);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createDocumentFragment()
     */
    public DocumentFragment createDocumentFragment() {
        return getProxyObject().createDocumentFragment();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createElement(java.lang.String)
     */
    public Element createElement(String tagName) throws DOMException {
        return getProxyObject().createElement(tagName);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createElementNS(java.lang.String, java.lang.String)
     */
    public Element createElementNS(String namespaceURI, String qualifiedName)
            throws DOMException {
        return getProxyObject().createElementNS(namespaceURI, qualifiedName);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createEntityReference(java.lang.String)
     */
    public EntityReference createEntityReference(String name)
            throws DOMException {
        return getProxyObject().createEntityReference(name);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createProcessingInstruction(java.lang.String, java.lang.String)
     */
    public ProcessingInstruction createProcessingInstruction(String target,
            String data) throws DOMException {
        return getProxyObject().createProcessingInstruction(target, data);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#createTextNode(java.lang.String)
     */
    public Text createTextNode(String data) {
        return getProxyObject().createTextNode(data);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getDoctype()
     */
    public DocumentType getDoctype() {
        return getProxyObject().getDoctype();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getDocumentElement()
     */
    public Element getDocumentElement() {
        return getProxyObject().getDocumentElement();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getDocumentURI()
     */
    public String getDocumentURI() {
        return getProxyObject().getDocumentURI();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getDomConfig()
     */
    public DOMConfiguration getDomConfig() {
        return getProxyObject().getDomConfig();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getElementById(java.lang.String)
     */
    public Element getElementById(String elementId) {
        return getProxyObject().getElementById(elementId);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName(String tagname) {
        return getProxyObject().getElementsByTagName(tagname);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getElementsByTagNameNS(java.lang.String, java.lang.String)
     */
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        return getProxyObject().getElementsByTagNameNS(namespaceURI, localName);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getImplementation()
     */
    public DOMImplementation getImplementation() {
        return getProxyObject().getImplementation();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getInputEncoding()
     */
    public String getInputEncoding() {
        return getProxyObject().getInputEncoding();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getStrictErrorChecking()
     */
    public boolean getStrictErrorChecking() {
        return getProxyObject().getStrictErrorChecking();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getXmlEncoding()
     */
    public String getXmlEncoding() {
        return getProxyObject().getXmlEncoding();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getXmlStandalone()
     */
    public boolean getXmlStandalone() {
        return getProxyObject().getXmlStandalone();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#getXmlVersion()
     */
    public String getXmlVersion() {
        return getProxyObject().getXmlVersion();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#importNode(org.w3c.dom.Node, boolean)
     */
    public Node importNode(Node importedNode, boolean deep) throws DOMException {
        return getProxyObject().importNode(importedNode, deep);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#normalizeDocument()
     */
    public void normalizeDocument() {
        getProxyObject().normalizeDocument();
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#renameNode(org.w3c.dom.Node, java.lang.String, java.lang.String)
     */
    public Node renameNode(Node n, String namespaceURI, String qualifiedName)
            throws DOMException {
        return getProxyObject().renameNode(n, namespaceURI, qualifiedName);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#setDocumentURI(java.lang.String)
     */
    public void setDocumentURI(String documentURI) {
        getProxyObject().setDocumentURI(documentURI);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#setStrictErrorChecking(boolean)
     */
    public void setStrictErrorChecking(boolean strictErrorChecking) {
        getProxyObject().setStrictErrorChecking(strictErrorChecking);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#setXmlStandalone(boolean)
     */
    public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
        getProxyObject().setXmlStandalone(xmlStandalone);
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Document#setXmlVersion(java.lang.String)
     */
    public void setXmlVersion(String xmlVersion) throws DOMException {
        getProxyObject().setXmlVersion(xmlVersion);
    }

    @Override
    public E getProxyObject() {
        return document;
    }

    @Override
    public void setProxyObject(E object) {
        document = (E) object;
        super.setProxyObject(document);
    }

    public NodeAtExist getNode(int nodeNr) throws DOMException {
        return getProxyObject().getNode(nodeNr);
    }

    public boolean hasReferenceNodes() {
        return getProxyObject().hasReferenceNodes();
    }

    @Override
    public XmldbURI getURI() {
        return getProxyObject().getURI();
    }

    @Override
    public int getDocId() {
        return getProxyObject().getDocId();
    }

    @Override
    public Object getUUID() {
        return getProxyObject().getUUID();
    }
}

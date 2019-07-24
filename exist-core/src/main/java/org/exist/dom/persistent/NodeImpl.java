/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.dom.persistent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.INode;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.storage.txn.Txn;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

import javax.xml.XMLConstants;

public abstract class NodeImpl<T extends NodeImpl> implements INode<DocumentImpl, T> {

    protected static final Logger LOG = LogManager.getLogger(NodeImpl.class);

    @Override
    public Node cloneNode(final boolean deep) {
        throw unsupported();
    }

    @Override
    public Node appendChild(final Node newChild) throws DOMException {
        if((newChild.getNodeType() == Node.DOCUMENT_NODE && newChild != getOwnerDocument()) || newChild.getOwnerDocument() != getOwnerDocument()) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Owning document IDs do not match");
        }

        throw unsupported();
    }

    @Override
    public Node removeChild(final Node oldChild) throws DOMException {
        throw unsupported();
    }

    @Override
    public Node replaceChild(final Node newChild, final Node oldChild) throws DOMException {
        throw unsupported();
    }

    @Override
    public Node insertBefore(final Node newChild, final Node refChild) throws DOMException {
        throw unsupported();
    }

    public void appendChildren(final Txn transaction, final NodeList nodes, final int child) throws DOMException {
        throw unsupported();
    }

    public Node removeChild(final Txn transaction, final Node oldChild) throws DOMException {
        throw unsupported();
    }

    public Node replaceChild(final Txn transaction, final Node newChild, final Node oldChild) throws DOMException {
        throw unsupported();
    }

    /**
     * Update a child node. This method will only update the child node
     * but not its potential descendant nodes.
     * @param transaction the transaction
     * @param oldChild node to update
     * @param newChild updated node
     * @throws DOMException in case of a DOM error
     * @return updated node
     */
    public IStoredNode updateChild(final Txn transaction, final Node oldChild, final Node newChild) throws DOMException {
        throw unsupported();
    }

    /**
     * Insert a list of nodes at the position before the reference
     * child.
     *
     * NOTE: You must call insertBefore on the parent node of the node that you
     * want to insert nodes before.
     * @param transaction the transaction
     * @param refChild target of param nodes
     * @param nodes list of nodes to be added to refChild
     */
    public void insertBefore(final Txn transaction, final NodeList nodes,final  Node refChild) throws DOMException {
        throw unsupported();
    }

    /**
     * Insert a list of nodes at the position following the reference
     * child.
     *
     * NOTE: You must call insertAfter on the parent node of the node that you want
     * to insert nodes after.
     * @param transaction the transaction
     * @param refChild target of param nodes
     * @param nodes list of nodes to be added to refChild
     */
    public void insertAfter(final Txn transaction, final NodeList nodes, final Node refChild) throws DOMException {
        throw unsupported();
    }

    public int getChildCount() {
        throw unsupported();
    }

    @Override
    public NodeList getChildNodes() {
        return new NodeListImpl();
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public Node getLastChild() {
        return null;
    }

    @Override
    public boolean hasAttributes() {
        return false;
    }

    @Override
    public NamedNodeMap getAttributes() {
        return null;
    }

    @Override
    public String getNodeValue() throws DOMException {
        return null;
    }

    @Override
    public void setNodeValue(final String value) throws DOMException {
        throw unsupported();
    }

    @Override
    public boolean hasChildNodes() {
        return false;
    }

    @Override
    public boolean isSupported(final String key, final String value) {
        throw unsupported();
    }

    @Override
    public void normalize() {
        throw unsupported();
    }

    @Override
    public String getBaseURI() {
        throw unsupported();
    }

    @Override
    public short compareDocumentPosition(final Node other) throws DOMException {
        throw unsupported();
    }

    @Override
    public String getTextContent() throws DOMException {
        throw unsupported();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        throw unsupported();
    }

    @Override
    public String lookupPrefix(final String namespaceURI) {
        throw unsupported();
    }

    @Override
    public boolean isDefaultNamespace(final String namespaceURI) {
        throw unsupported();
    }

    @Override
    public String lookupNamespaceURI(final String prefix) {
        throw unsupported();
    }

    @Override
    public boolean isEqualNode(final Node arg) {
        throw unsupported();
    }

    @Override
    public Object getFeature(final String feature, final String version) {
        throw unsupported();
    }

    @Override
    public Object getUserData(final String key) {
        throw unsupported();
    }

    @Override
    public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
        throw unsupported();
    }

    @Override
    public String getPrefix() {
        switch(getNodeType()) {
            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
                final String prefix = getQName().getPrefix();
                return prefix;

            default:
                return null;
        }
    }

    @Override
    public void setPrefix(final String prefix) throws DOMException {
        if(prefix == null || getNodeType() == Node.DOCUMENT_NODE) {
            return;
        }

        final QName nodeName = getQName();
        if(nodeName != null) {
            setQName(new QName(nodeName.getLocalPart(), nodeName.getNamespaceURI(), prefix));
        }
    }

    @Override
    public final String getNamespaceURI() {
        switch(getNodeType()) {
            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
                final String nsUri = getQName().getNamespaceURI();
                if(nsUri.equals(XMLConstants.NULL_NS_URI)) {
                    return null;
                } else {
                    return nsUri;
                }

            default:
                return null;
        }
    }

    @Override
    public String getLocalName() {
        if(this instanceof NamedNode) {
            return getQName().getLocalPart();
        } else {
            return null;
        }
    }

    @Override
    public final String getNodeName() {
        switch(getNodeType()) {
            case Node.DOCUMENT_TYPE_NODE:
                return ((DocumentTypeImpl)this).getName();

            case Node.DOCUMENT_NODE:
                return "#document";

            case Node.DOCUMENT_FRAGMENT_NODE:
                return "#document-fragment";

            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
                return getQName().getStringValue();

            case Node.PROCESSING_INSTRUCTION_NODE:
                return ((ProcessingInstructionImpl)this).getTarget();

            case Node.TEXT_NODE:
                return "#text";

            case Node.COMMENT_NODE:
                return "#comment";

            case Node.CDATA_SECTION_NODE:
                return "#cdata-section";

            default:
                return "#unknown";
        }
    }

    protected DOMException unsupported() {
        return new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented on class: " + getClass().getName());
    }
}

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
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    @Override
    public Node appendChild(final Node child) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    @Override
    public Node removeChild(final Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    @Override
    public Node replaceChild(final Node newChild, final Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    @Override
    public Node insertBefore(final Node newChild, final Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    public void appendChildren(final Txn transaction, final NodeList nodes, final int child) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    public Node removeChild(final Txn transaction, final Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    public Node replaceChild(final Txn transaction, final Node newChild, final Node oldChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    /**
     * Update a child node. This method will only update the child node
     * but not its potential descendant nodes.
     *
     * @param oldChild
     * @param newChild
     * @throws DOMException
     */
    public IStoredNode updateChild(final Txn transaction, final Node oldChild, final Node newChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    /**
     * Insert a list of nodes at the position before the reference
     * child.
     * <p/>
     * NOTE: You must call insertBefore on the parent node of the node that you
     * want to insert nodes before.
     */
    public void insertBefore(final Txn transaction, final NodeList nodes,final  Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "not implemented on class " + getClass().getName());
    }

    /**
     * Insert a list of nodes at the position following the reference
     * child.
     * <p/>
     * NOTE: You must call insertAfter on the parent node of the node that you want
     * to insert nodes after.
     */
    public void insertAfter(final Txn transaction, final NodeList nodes, final Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "insertAfter(Txn transaction, NodeList nodes, Node refChild) " +
                "not implemented on class " + getClass().getName());
    }

    public int getChildCount() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getChildCount() not implemented on class " + getClass().getName());
    }

    @Override
    public NodeList getChildNodes() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getChildNodes() not implemented on class " + getClass().getName());
    }

    /**
     * Note: Typically you should call {@link org.w3c.dom.Node#hasChildNodes()}
     * first.
     *
     * @see org.w3c.dom.Node#getFirstChild()
     */
    @Override
    public Node getFirstChild() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getFirstChild() not implemented on class " + getClass().getName());
    };

    @Override
    public Node getLastChild() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getLastChild() not implemented on class " + getClass().getName());
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
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getNodeValue() not implemented on class " + getClass().getName());
    }

    @Override
    public void setNodeValue(final String value) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setNodeValue(String value) not implemented on class " + getClass().getName());
    }

    @Override
    public boolean hasChildNodes() {
        return getChildCount() > 0;
    }

    @Override
    public boolean isSupported(final String key, final String value) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "isSupported(String key, String value) not implemented on class " + getClass().getName());
    }

    @Override
    public void normalize() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "normalize() not implemented on class " + getClass().getName());
    }

    @Override
    public String getBaseURI() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getBaseURI() not implemented on class " + getClass().getName());
    }

    @Override
    public short compareDocumentPosition(final Node other) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "compareDocumentPosition(Node other) not implemented on class " + getClass().getName());
    }

    @Override
    public String getTextContent() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getTextContent() not implemented on class " + getClass().getName());
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setTextContent(String textContent) not implemented on class " + getClass().getName());
    }

    @Override
    public boolean isSameNode(final Node other) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "isSameNode(Node other) not implemented on class " + getClass().getName());
    }

    @Override
    public String lookupPrefix(final String namespaceURI) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "lookupPrefix(String namespaceURI) not implemented on class " + getClass().getName());
    }

    @Override
    public boolean isDefaultNamespace(final String namespaceURI) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "isDefaultNamespace(String namespaceURI) not implemented on class " + getClass().getName());
    }

    @Override
    public String lookupNamespaceURI(final String prefix) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "lookupNamespaceURI(String prefix) not implemented on class " + getClass().getName());
    }

    @Override
    public boolean isEqualNode(final Node arg) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "isEqualNode(Node arg) not implemented on class " + getClass().getName());
    }

    @Override
    public Object getFeature(final String feature, final String version) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getFeature(String feature, String version) not implemented on class " + getClass().getName());
    }

    @Override
    public Object getUserData(final String key) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getUserData(String key) not implemented on class " + getClass().getName());
    }

    @Override
    public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setUserData(String key, Object data, UserDataHandler handler) not implemented on class " + getClass().getName());
    }

    @Override
    public String getPrefix() {
        final QName nodeName = getQName();
        final String prefix = nodeName.getPrefix();
        return prefix == null ? XMLConstants.DEFAULT_NS_PREFIX : prefix;
    }

    @Override
    public void setPrefix(final String prefix) throws DOMException {
        final QName nodeName = getQName();
        if(nodeName != null) {
            setQName(new QName(nodeName.getLocalPart(), nodeName.getNamespaceURI(), prefix));
        }
    }

    @Override
    public String getNamespaceURI() {
        return getQName().getNamespaceURI();
    }

    @Override
    public String getLocalName() {
        return getQName().getLocalPart();
    }

    @Override
    public String getNodeName() {
        return getQName().getStringValue();
    }
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
package org.exist.dom.persistent;

import org.exist.Namespaces;
import org.exist.numbering.NodeId;
import org.exist.storage.Signatures;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.util.pool.NodePool;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

/**
 * TextImpl.java
 * 
 * @author wolf
 *
 */
public class TextImpl extends CharacterDataImpl implements Text {

    public TextImpl() {
        super(Node.TEXT_NODE);
    }

    public TextImpl( String data ) {
        super(Node.TEXT_NODE, data);
    }

    public TextImpl( NodeId nodeId, String data ) {
        super(Node.TEXT_NODE, nodeId, data);
    }

    @Override
    public String getLocalName() {
        return "";
    }

    @Override
    public String getNamespaceURI() {
        return "";
    }

    /**
     * Serializes a (persistent DOM) Text to a byte array
     *
     * data = signature nodeIdUnitsLength nodeId cdata
     *
     * signature = [byte] 0x0
     *
     * nodeIdUnitsLength = [short] (2 bytes) The number of units of the text's NodeId
     * nodeId = {@see org.exist.numbering.DLNBase#serialize(byte[], int)}
     *
     * cdata = eUtf8
     *
     * eUtf8 = {@see org.exist.util.UTF8#encode(java.lang.String, byte[], int)}
     */
    @Override
    public byte[] serialize() {
        final int nodeIdLen = nodeId.size();
        final byte[] data = ByteArrayPool.getByteArray(LENGTH_SIGNATURE_LENGTH + nodeIdLen + 
            NodeId.LENGTH_NODE_ID_UNITS + cdata.UTF8Size());
        int pos = 0;
        data[pos] = (byte) (Signatures.Char << 0x5);
        pos += LENGTH_SIGNATURE_LENGTH;
        ByteConversion.shortToByte((short) nodeId.units(), data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId.serialize(data, pos);
        pos += nodeIdLen;
        cdata.UTF8Encode(data, pos);
        return data;
    }

    public static StoredNode deserialize(byte[] data, int start, int len,
            DocumentImpl doc, boolean pooled) {
        TextImpl text;
        if (pooled)
            {text = (TextImpl) NodePool.getInstance().borrowNode(Node.TEXT_NODE);}
        else
            {text = new TextImpl();}
        int pos = start;
        pos += LENGTH_SIGNATURE_LENGTH;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        text.setNodeId(dln);
        int nodeIdLen = dln.size();
        pos += nodeIdLen;
        text.cdata = UTF8.decode(data, pos, len - (LENGTH_SIGNATURE_LENGTH +
            nodeIdLen + NodeId.LENGTH_NODE_ID_UNITS));
        return text;
    }

    @Override
    public Text splitText(int offset) throws DOMException {
        return null;
    }

    @Override
    public String toString(boolean top) {
        if (top) {
            final StringBuilder result = new StringBuilder();
            result.append("<exist:text ");
            result.append("xmlns:exist=\"" + Namespaces.EXIST_NS + "\" ");
            result.append("exist:id=\"");
            result.append(getNodeId());
            result.append("\" exist:source=\"");
            result.append(((DocumentImpl)getOwnerDocument()).getFileURI());
            result.append("\">");
            result.append(getData());
            result.append("</exist:text>");
            return result.toString();
        } else {
            return toString();
        }
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public boolean hasChildNodes() {
        return false;
    }

    @Override
    public Node getFirstChild() {
        //bad implementations don't call hasChildNodes before
        return null;
    }

    /** ? @see org.w3c.dom.Text#isElementContentWhitespace()
     */
    @Override
    public boolean isElementContentWhitespace() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return false;
    }

    /** ? @see org.w3c.dom.Text#getWholeText()
     */
    @Override
    public String getWholeText() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /** ? @see org.w3c.dom.Text#replaceWholeText(java.lang.String)
     */
    @Override
    public Text replaceWholeText(String content) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /** ? @see org.w3c.dom.Node#getBaseURI()
     */
    @Override
    public String getBaseURI() {
        final Node parent = getParentNode();
        if (parent != null)
            {return parent.getBaseURI();}
        else
            {return null;}
    }

    /** ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
     */
    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return 0;
    }

    /** ? @see org.w3c.dom.Node#getTextContent()
     */
    @Override
    public String getTextContent() throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /** ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
     */
    @Override
    public void setTextContent(String textContent) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    /** ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
     */
    @Override
    public boolean isSameNode(Node other) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return false;
    }

    /** ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
     */
    @Override
    public String lookupPrefix(String namespaceURI) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /** ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
     */
    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return false;
    }

    /** ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
     */
    @Override
    public String lookupNamespaceURI(String prefix) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /** ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
     */
    @Override
    public boolean isEqualNode(Node arg) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return false;
    }

    /** ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
     */
    @Override
    public Object getFeature(String feature, String version) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
     */
    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
     */
    @Override
    public Object getUserData(String key) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }
}


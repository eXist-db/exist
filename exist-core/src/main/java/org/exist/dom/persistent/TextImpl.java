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

import javax.xml.XMLConstants;

/**
 * TextImpl.java
 *
 * @author wolf
 */
public class TextImpl extends AbstractCharacterData implements Text {

    public TextImpl() {
        super(Node.TEXT_NODE);
    }

    public TextImpl(final String data) {
        super(Node.TEXT_NODE, data);
    }

    public TextImpl(final NodeId nodeId, final String data) {
        super(Node.TEXT_NODE, nodeId, data);
    }

    /**
     * Serializes a (persistent DOM) Text to a byte array
     *
     * data = signature nodeIdUnitsLength nodeId cdata
     *
     * signature = [byte] 0x0
     *
     * nodeIdUnitsLength = [short] (2 bytes) The number of units of the text's NodeId
     * nodeId = See {@link org.exist.numbering.DLNBase#serialize(byte[], int)}
     *
     * cdata = eUtf8
     *
     * eUtf8 = See {@link org.exist.util.UTF8#encode(java.lang.String, byte[], int)}
     *
     * @return the returned byte array after use must be returned to the ByteArrayPool
     *     by calling {@link ByteArrayPool#releaseByteArray(byte[])}
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

    public static StoredNode deserialize(final byte[] data, final int start, final int len,
            final DocumentImpl doc, final boolean pooled) {
        final TextImpl text;
        if(pooled) {
            text = (TextImpl) NodePool.getInstance().borrowNode(Node.TEXT_NODE);
        } else {
            text = new TextImpl();
        }
        int pos = start;
        pos += LENGTH_SIGNATURE_LENGTH;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        text.setNodeId(dln);
        final int nodeIdLen = dln.size();
        pos += nodeIdLen;
        text.cdata = UTF8.decode(data, pos, len - (LENGTH_SIGNATURE_LENGTH +
            nodeIdLen + NodeId.LENGTH_NODE_ID_UNITS));
        return text;
    }

    @Override
    public String toString(final boolean top) {
        if(top) {
            return "<exist:text " + "xmlns:exist=\"" + Namespaces.EXIST_NS + "\" " + "exist:id=\"" +
                    getNodeId() + "\" exist:source=\"" + getOwnerDocument().getFileURI() + "\">" +
                    getData() + "</exist:text>";
        } else {
            return toString();
        }
    }

    @Override
    public String getWholeText() {
        return null;
    }

    @Override
    public boolean isElementContentWhitespace() {
        return false;
    }

    @Override
    public Text replaceWholeText(final String content) throws DOMException {
        return null;
    }

    @Override
    public Text splitText(final int offset) throws DOMException {
        return null;
    }

    @Override
    public String getBaseURI() {
        final Node parent = getParentNode();
        if(parent != null) {
            return parent.getBaseURI();
        } else {
            return null;
        }
    }

    @Override
    public short compareDocumentPosition(final Node other) throws DOMException {
        return 0;
    }

    @Override
    public String lookupPrefix(final String namespaceURI) {
        return null;
    }

    @Override
    public boolean isDefaultNamespace(final String namespaceURI) {
        return false;
    }

    @Override
    public String lookupNamespaceURI(final String prefix) {
        return null;
    }

    @Override
    public boolean isEqualNode(final Node arg) {
        return false;
    }

    @Override
    public Object getFeature(final String feature, final String version) {
        return null;
    }

    @Override
    public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
        return null;
    }

    @Override
    public Object getUserData(final String key) {
        return null;
    }
}


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
package org.exist.dom.persistent;

import org.exist.numbering.NodeId;
import org.exist.storage.Signatures;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.util.XMLString;
import org.exist.xquery.Expression;
import org.w3c.dom.CDATASection;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class CDATASectionImpl extends AbstractCharacterData implements CDATASection {

    public CDATASectionImpl() {
        this((Expression) null);
    }

    public CDATASectionImpl(final Expression expression) {
        super(expression, Node.CDATA_SECTION_NODE);
    }

    public CDATASectionImpl(final NodeId nodeId, final String data) {
        this((Expression) null, nodeId, data);
    }

    public CDATASectionImpl(final Expression expression, final NodeId nodeId, final String data) {
        super(expression, Node.CDATA_SECTION_NODE, nodeId, data);
    }

    public CDATASectionImpl(final NodeId nodeId) {
        this((Expression) null, nodeId);
    }

    public CDATASectionImpl(final Expression expression, final NodeId nodeId) {
        super(expression, Node.CDATA_SECTION_NODE, nodeId);
    }

    public CDATASectionImpl(final XMLString data) {
        this((Expression) null, data);
    }

    public CDATASectionImpl(final Expression expression, final XMLString data) {
        super(expression, Node.CDATA_SECTION_NODE);
        this.cdata = data;
    }

    public CDATASectionImpl(final String data) {
        this((Expression) null, data);
    }

    public CDATASectionImpl(final Expression expression, final String data) {
        super(expression, Node.CDATA_SECTION_NODE, data);
    }

    @Override
    /**
     * Serializes a (persistent DOM) CDATA Section to a byte array
     *
     * data = signature nodeIdUnitsLength nodeId cdata
     *
     * signature = [byte] 0xA0
     *
     * nodeIdUnitsLength = [short] (2 bytes) The number of units of the cdata section's NodeId
     * nodeId = {@see org.exist.numbering.DLNBase#serialize(byte[], int)}
     *
     * cdata = eUtf8
     *
     * eUtf8 = {@see org.exist.util.UTF8#encode(java.lang.String, byte[], int)}
     *
     * @return the returned byte array after use must be returned to the ByteArrayPool
     *     by calling {@link ByteArrayPool#releaseByteArray(byte[])}
     */
    public byte[] serialize() {
        final int nodeIdLen = nodeId.size();
        final byte[] data = ByteArrayPool.getByteArray(LENGTH_SIGNATURE_LENGTH + NodeId.LENGTH_NODE_ID_UNITS +
            nodeIdLen + cdata.UTF8Size());
        int pos = 0;
        data[pos] = (byte) (Signatures.Cdata << 0x5);
        pos += LENGTH_SIGNATURE_LENGTH;
        ByteConversion.shortToByte((short) nodeId.units(), data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId.serialize(data, pos);
        pos += nodeIdLen;
        cdata.UTF8Encode(data, pos);
        return data;
    }

    public static StoredNode deserialize(final byte[] data, final int start,
            final int len, final DocumentImpl doc, final boolean pooled) {
        int pos = start;
        pos += LENGTH_SIGNATURE_LENGTH;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        int nodeIdLen = dln.size();
        pos += nodeIdLen;
        final CDATASectionImpl cdata = new CDATASectionImpl(null, dln);
        cdata.cdata = UTF8.decode(data, pos, len - (pos - start));
        return cdata;
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
}

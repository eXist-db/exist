/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
package org.exist.dom.persistent;

import org.exist.numbering.NodeId;
import org.exist.storage.Signatures;
import org.exist.util.ByteConversion;
import org.exist.util.pool.NodePool;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import javax.xml.XMLConstants;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Persistent implementation of a DOM processing-instruction node.
 *
 * @author wolf
 */
public class ProcessingInstructionImpl extends StoredNode implements ProcessingInstruction {

    public static final int LENGTH_TARGET_DATA = 4; //Sizeof int;

    protected String target = null;
    protected String data = null;

    public ProcessingInstructionImpl() {
        super(Node.PROCESSING_INSTRUCTION_NODE);
    }

    public ProcessingInstructionImpl(final NodeId nodeId, final String target, final String data) {
        super(Node.PROCESSING_INSTRUCTION_NODE, nodeId);
        this.target = target;
        this.data = data;
    }

    public ProcessingInstructionImpl(final String target, final String data) {
        this(null, target, data);
    }

    @Override
    public void clear() {
        super.clear();
        target = null;
        data = null;
    }

    /**
     * Gets the target attribute of the ProcessingInstructionImpl object
     *
     * @return The target value
     */
    @Override
    public String getTarget() {
        return target;
    }

    /**
     * Sets the target attribute of the ProcessingInstructionImpl object
     *
     * @param target The new target value
     */
    public void setTarget(final String target) {
        this.target = target;
    }

    @Override
    public String getNodeValue() throws DOMException {
        return getData();
    }

    /**
     * Gets the data attribute of the ProcessingInstructionImpl object
     *
     * @return The data value
     */
    @Override
    public String getData() {
        return data;
    }

    /**
     * Sets the data attribute of the ProcessingInstructionImpl object
     *
     * @param data The new data value
     */
    @Override
    public void setData(final String data) {
        this.data = data;
    }

    /**
     * ? @see org.w3c.dom.Node#getBaseURI()
     */
    @Override
    public String getBaseURI() {
        final StoredNode parent = getParentStoredNode();
        if(parent != null) {
            return parent.getBaseURI();
        } else {
            return getOwnerDocument().getBaseURI();
        }
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("<?");
        buf.append(target);
        buf.append(" ");
        buf.append(data);
        buf.append(" ?>");
        return buf.toString();
    }

    /**
     * Serializes a (persistent DOM) Processing Instruction to a byte array
     *
     * data = signature nodeIdUnitsLength nodeId targetLength target contentLength content
     *
     * signature = [byte] 0x40
     *
     * nodeIdUnitsLength = [short] (2 bytes) The number of units of the processing instruction's NodeId
     * nodeId = @see org.exist.numbering.DLNBase#serialize(byte[], int)
     *
     * targetLength = [int] (4 bytes) The length of the target string in bytes
     * target = jUtf8
     *
     * contentLength = [int] (4 bytes) The length of the data string in bytes
     * content = jUtf8
     *
     * jUtf8 = @see java.io.DataOutputStream#writeUTF(java.lang.String)
     */
    @Override
    public byte[] serialize() {

        final byte[] td = target.getBytes(UTF_8);
        final byte[] dd = data.getBytes(UTF_8);

        final int nodeIdLen = nodeId.size();
        final byte[] d = new byte[td.length + dd.length + nodeIdLen + 7];
        int pos = 0;
        d[pos] = (byte) (Signatures.Proc << 0x5);
        pos += LENGTH_SIGNATURE_LENGTH;
        ByteConversion.shortToByte((short) nodeId.units(), d, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId.serialize(d, pos);
        pos += nodeIdLen;
        ByteConversion.intToByte(td.length, d, pos);
        pos += LENGTH_TARGET_DATA;
        System.arraycopy(td, 0, d, pos, td.length);
        pos += td.length;
        System.arraycopy(dd, 0, d, pos, dd.length);
        return d;
    }

    public static StoredNode deserialize(final byte[] data, final int start, final int len, final DocumentImpl doc, final boolean pooled) {
        int pos = start;
        pos += LENGTH_SIGNATURE_LENGTH;

        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;

        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        final int nodeIdLen = dln.size();
        pos += nodeIdLen;

        final int l = ByteConversion.byteToInt(data, pos);
        pos += LENGTH_TARGET_DATA;

        final String target = new String(data, pos, l, UTF_8);
        pos += l;

        final String cdata = new String(data, pos, len - (pos - start), UTF_8);

        //OK : we have the necessary material to build the processing instruction
        final ProcessingInstructionImpl pi;
        if(pooled) {
            pi = (ProcessingInstructionImpl) NodePool.getInstance().borrowNode(Node.PROCESSING_INSTRUCTION_NODE);
        } else {
            pi = new ProcessingInstructionImpl();
        }

        pi.setTarget(target);
        pi.data = cdata;
        pi.setNodeId(dln);

        return pi;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public Node getFirstChild() {
        return null;
    }
}


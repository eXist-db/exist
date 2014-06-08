/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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

package org.exist.dom;

import org.exist.numbering.NodeId;
import org.exist.storage.Signatures;
import org.exist.util.ByteConversion;
import org.exist.util.pool.NodePool;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

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

    public ProcessingInstructionImpl(NodeId nodeId, String target, String data) {
        super(Node.PROCESSING_INSTRUCTION_NODE, nodeId);
        this.target = target;
        this.data = data;
    }

    public ProcessingInstructionImpl(String target, String data) {
        this(null, target, data);
    }

    @Override
    public void clear() {
        super.clear();
        target = null;
        data = null;
    } 

    /**
     *  Gets the target attribute of the ProcessingInstructionImpl object
     *
     *@return    The target value
     */
    public String getTarget() {
        return target;
    }

    /**
     *  Sets the target attribute of the ProcessingInstructionImpl object
     *
     *@param  target  The new target value
     */
    public void setTarget(final String target) {
        this.target = target;
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNodeName()
     */
    @Override
    public String getNodeName() {
        return target;
    }

    @Override
    public String getLocalName() {
        return target;
    }

    @Override
    public String getNamespaceURI() {
        return "";
    }

    /**
     *  Gets the data attribute of the ProcessingInstructionImpl object
     *
     *@return    The data value
     */
    public String getData() {
        return data;
    }

    /**
     *  Sets the data attribute of the ProcessingInstructionImpl object
     *
     *@param  data  The new data value
     */
    public void setData(final String data) {
        this.data = data;
    }

    /** ? @see org.w3c.dom.Node#getBaseURI()
     */
    @Override
    public String getBaseURI() {
        final StoredNode parent = getParentStoredNode();
        if (parent != null )
            {return parent.getBaseURI();}
        return getDocument().getBaseURI();
    }

    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append( "<?" );
        buf.append( target );
        buf.append( " " );
        buf.append( data );
        buf.append( " ?>" );
        return buf.toString();
    }

    @Override
    public byte[] serialize() {

        byte[] td = target.getBytes(UTF_8);
        byte[] dd = data.getBytes(UTF_8);

        int nodeIdLen = nodeId.size();
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
        System.arraycopy( td, 0, d, pos, td.length);
        pos += td.length;
        System.arraycopy( dd, 0, d, pos, dd.length);
        return d;
    }

    public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc, boolean pooled) {
        int pos = start;
        pos += LENGTH_SIGNATURE_LENGTH;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        int nodeIdLen = dln.size();
        pos += nodeIdLen;
        int l = ByteConversion.byteToInt(data, pos);
        pos += LENGTH_TARGET_DATA;
        String target;
        target = new String(data, pos, l, UTF_8);
        pos += l;
        String cdata;
        cdata = new String(data, pos, len - (pos - start), UTF_8);
        //OK : we have the necessary material to build the processing instruction
        ProcessingInstructionImpl pi;
        if(pooled)
            {pi = (ProcessingInstructionImpl) NodePool.getInstance().borrowNode(Node.PROCESSING_INSTRUCTION_NODE);}
        else
            {pi = new ProcessingInstructionImpl();}
        pi.setTarget(target);
        pi.data = cdata;
        pi.setNodeId(dln);
        return pi;
    }

    @Override
    public boolean hasChildNodes() {
        return false;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public Node getFirstChild() {
        //bad implementations don't call hasChildNodes before
        return null;
    }

}

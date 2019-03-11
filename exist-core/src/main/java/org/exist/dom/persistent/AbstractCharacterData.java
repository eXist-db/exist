/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-2014,  Wolfgang Meier (wolfgang@exist-db.org)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 * 
 */
package org.exist.dom.persistent;

import org.exist.numbering.NodeId;
import org.exist.storage.btree.Value;
import org.exist.util.UTF8;
import org.exist.util.XMLString;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

public abstract class AbstractCharacterData extends StoredNode implements CharacterData {

    protected XMLString cdata = null;

    public AbstractCharacterData(final short nodeType) {
        super(nodeType);
    }

    public AbstractCharacterData(final short nodeType, final NodeId nodeId) {
        super(nodeType, nodeId);
    }

    public AbstractCharacterData(final short nodeType, final NodeId nodeId, final String data) {
        super(nodeType, nodeId);
        cdata = new XMLString(data.toCharArray());
    }

    public AbstractCharacterData(final short nodeType, final String data) {
        super(nodeType);
        cdata = new XMLString(data.toCharArray());
    }

    public AbstractCharacterData(final short nodeType, final char[] data, final int start, final int howmany) {
        super(nodeType);
        cdata = new XMLString(data, start, howmany);
    }

    @Override
    public final int getChildCount() {
        return 0;
    }

    @Override
    public final Node getFirstChild() {
        return null;
    }

    @Override
    public void clear() {
        super.clear();
        cdata.reset();
    }

    @Override
    public void appendData(final String arg) throws DOMException {
        if(cdata == null) {
            cdata = new XMLString(arg.toCharArray());
        } else {
            cdata.append(arg);
        }
    }

    @Override
    public void deleteData(final int offset, final int count) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(cdata != null) {
            if(offset > cdata.length()) {
                throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
            }

            if(offset + count > cdata.length()) {
                cdata.delete(offset, cdata.length() - offset);
            } else {
                cdata.delete(offset, count);
            }
        }
    }

    @Override
    public String getData() throws DOMException {
        if(cdata == null) {
            return null;
        }
        return cdata.toString();
    }

    public XMLString getXMLString() {
        return cdata;
    }

    @Override
    public int getLength() {
        return cdata.length();
    }

    @Override
    public String getNodeValue() {
        return cdata.toString();
    }

    @Override
    public void setNodeValue(final String value) throws DOMException {
        setData(value);
    }

    @Override
    public String getTextContent() throws DOMException {
        return getNodeValue();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        setNodeValue(textContent);
    }

    @Override
    public void insertData(final int offset, final String arg) throws DOMException {
        if(offset < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(cdata == null) {
            cdata = new XMLString(arg.toCharArray());
        } else {
            if(offset > cdata.length()) {
                throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
            }
            cdata.insert(offset, arg);
        }
    }

    @Override
    public void replaceData(final int offset, int count, final String arg) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(cdata == null) {
            throw new DOMException(DOMException.DOMSTRING_SIZE_ERR, "string index out of bounds");
        } else {
            if (offset > cdata.length()) {
                throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
            }

            if(offset + count > cdata.length()) {
                count = cdata.length() - offset;
            }

            cdata.replace(offset, count, arg);
        }
    }

    @Override
    public void setData(final String data) throws DOMException {
        if(cdata == null) {
            cdata = new XMLString(data.toCharArray());
        } else {
            cdata.setData(data.toCharArray(), 0, data.length());
        }
    }

    public void setData(final XMLString data) throws DOMException {
        cdata = data;
    }

    public void setData(final char[] data, final int start, final int howmany) throws DOMException {
        if(cdata == null) {
            cdata = new XMLString(data, start, howmany);
        } else {
            cdata.setData(data, start, howmany);
        }
    }

    @Override
    public String substringData(final int offset, int count) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(cdata == null) {
            throw new DOMException(DOMException.DOMSTRING_SIZE_ERR, "string index out of bounds");
        }

        if(offset > cdata.length()) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(offset + count > cdata.length()) {
            count = cdata.length() - offset;
        }

        return cdata.substring(offset, count);
    }

    @Override
    public String toString() {
        if(cdata == null) {
            return "";
        }
        return cdata.toString();
    }

    /**
     * Release all resources hold by this object.
     */
    @Override
    public void release() {
        cdata.reset();
        super.release();
    }

    public static XMLString readData(final NodeId nodeId, final Value value, final XMLString string) {
        final int nodeIdLen = nodeId.size();
        UTF8.decode(value.data(), value.start() + 3 + nodeIdLen, value.getLength() - 3 - nodeIdLen, string);
        return string;
    }

    public static int getStringLength(final NodeId nodeId, final Value value) {
        final int nodeIdLen = nodeId.size();
        return value.getLength() - 3 - nodeIdLen;
    }
}
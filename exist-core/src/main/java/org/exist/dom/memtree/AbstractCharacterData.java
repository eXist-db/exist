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
package org.exist.dom.memtree;

import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;


public abstract class AbstractCharacterData extends NodeImpl implements CharacterData {

    public AbstractCharacterData(final DocumentImpl doc, final int nodeNumber) {
        this(null, doc, nodeNumber);
    }

    public AbstractCharacterData(final Expression expression, final DocumentImpl doc, final int nodeNumber) {
        super(expression, doc, nodeNumber);
    }

    @Override
    public int getLength() {
        return document.alphaLen[nodeNumber];
    }

    @Override
    public String getData() throws DOMException {
        return new String(document.characters, document.alpha[nodeNumber],
            document.alphaLen[nodeNumber]);
    }

    @Override
    public String substringData(final int offset, final int count) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int length = document.alphaLen[nodeNumber];
        final int inDocOffset = document.alpha[nodeNumber];
        if(offset > length) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(offset + count > length) {
            return new String(document.characters, inDocOffset + offset, length - offset);
        } else {
            return new String(document.characters, inDocOffset + offset, length);
        }
    }

    @Override
    public void replaceData(final int offset, int count, final String arg) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int existingDataLen = document.alphaLen[nodeNumber];
        if(offset > existingDataLen) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(offset + count > existingDataLen) {
            count = existingDataLen - offset;
        }

        final int len = arg.length();
        final int existingCharactersLen = document.characters.length;
        final int existingDataOffset = document.alpha[nodeNumber];

        // 1) create a new array of the correct size for the data
        final int change = len - count;
        final int newCharactersLength = existingCharactersLen + change;
        final char newCharacters[] = new char[newCharactersLength];

        // 2) copy everything from document.characters to newCharacters that is before our offset
        System.arraycopy(document.characters, 0, newCharacters, 0, existingDataOffset + offset);

        // 3) insert our replacement data at the offset
        System.arraycopy(arg.toCharArray(), 0, newCharacters, existingDataOffset + offset, len);

        // 4) copy everything from document.characters to newCharacters that is after our offset + count
        final int remainingExistingCharacters;
        if(len > 0 && existingDataLen < len) {
            // document.characters is expanding or staying the same length
            remainingExistingCharacters = existingCharactersLen - count;
        } else {
            // empty `data` (i.e. replacement), or shrinking of value_
            remainingExistingCharacters = existingCharactersLen - existingDataOffset - offset - count;
        }
        System.arraycopy(document.characters, existingDataOffset + offset + count, newCharacters, existingDataOffset + offset + len, remainingExistingCharacters);

        // 5) replace document.characters with our newCharacters
        document.characters = newCharacters;
        document.alphaLen[nodeNumber] = existingDataLen + change;

        // 6) renumber all offsets following our offset
        for(int i = nodeNumber + 1; i < document.alpha.length; i++) {
            if(document.alpha[i] > -1) {
                document.alpha[i] += change;
            }
        }
    }

    @Override
    public void insertData(final int offset, final String arg) throws DOMException {
        if(offset < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int existingDataLen = document.alphaLen[nodeNumber];
        if(offset > existingDataLen) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int len = arg.length();
        final int existingDataOffset = document.alpha[nodeNumber];

        // expand space for existing data and set

        // 1) create a new array of the correct size for the data
        final int existingCharactersLen = document.characters.length;
        final int extraRequired = len;
        final int newCharactersLen = existingCharactersLen + extraRequired;
        final char newCharacters[] = new char[newCharactersLen];

        // 2) copy everything from data to newData that is upto the end of our offset + provided offset
        System.arraycopy(document.characters, 0, newCharacters, 0, existingDataOffset + offset);

        // 3) insert our new data at the offset
        System.arraycopy(arg.toCharArray(), 0, newCharacters, existingDataOffset + offset, len);

        // 4) copy everything from data to newData that is after our our offset + provided offset
        final int remainingExistingCharacters = existingCharactersLen - (existingDataOffset + existingDataLen);
        System.arraycopy(document.characters, existingDataOffset + offset, newCharacters, existingDataOffset + offset + len, remainingExistingCharacters);

        // 5) replace document.characters with our new characters
        document.characters = newCharacters;
        document.alphaLen[nodeNumber] = existingDataLen + len;

        // 6) renumber all offsets following our offset
        for(int i = nodeNumber + 1; i < document.alpha.length; i++) {
            document.alpha[i] += extraRequired;
        }
    }

    @Override
    public void appendData(final String arg) throws DOMException {
        if(arg == null || arg.isEmpty()) {
            return;
        }

        final int len = arg.length();
        final int existingDataOffset = document.alpha[nodeNumber];
        final int existingDataLen = document.alphaLen[nodeNumber];

        // expand space for existing data and set

        // 1) create a new array of the correct size for the data
        final int existingCharactersLen = document.characters.length;
        final int extraRequired = len;
        final int newCharactersLen = existingCharactersLen + extraRequired;
        final char newCharacters[] = new char[newCharactersLen];

        // 2) copy everything from data to newData that is upto the end of our offset + len
        System.arraycopy(document.characters, 0, newCharacters, 0, existingDataOffset + existingDataLen);

        // 3) insert our new data after the existing data
        System.arraycopy(arg.toCharArray(), 0, newCharacters, existingDataOffset + existingDataLen, len);

        // 4) copy everything from data to newData that is after our our offset + len
        final int remainingExistingCharacters = existingCharactersLen - (existingDataOffset + existingDataLen);
        System.arraycopy(document.characters, existingDataOffset + existingDataLen, newCharacters, existingDataOffset + existingDataLen + len, remainingExistingCharacters);

        // 5) replace document.characters with our new characters
        document.characters = newCharacters;
        document.alphaLen[nodeNumber] = existingDataLen + len;

        // 6) renumber all offsets following our offset
        for(int i = nodeNumber + 1; i < document.alpha.length; i++) {
            document.alpha[i] += extraRequired;
        }
    }

    @Override
    public void setData(String data) throws DOMException {
        if(data == null) {
            data = "";
        }

        final int len = data.length();
        final int existingDataOffset = document.alpha[nodeNumber];
        final int existingDataLen = document.alphaLen[nodeNumber];

        if(len <= existingDataLen) {
            // replace existing data

            System.arraycopy(data.toCharArray(), 0, document.characters, existingDataOffset, len);
            document.alphaLen[nodeNumber] = len;

        } else {
            // expand space for existing data and set

            // 1) create a new array of the correct size for the data
            final int existingCharactersLen = document.characters.length;
            final int extraRequired = len - existingDataLen;
            final int newCharactersLen = existingCharactersLen + extraRequired;
            final char newCharacters[] = new char[newCharactersLen];

            // 2) copy everything from data to newData that is before our offset
            System.arraycopy(document.characters, 0, newCharacters, 0, existingDataOffset);

            // 3) insert our new data
            System.arraycopy(data.toCharArray(), 0, newCharacters, existingDataOffset, len);

            // 4) copy everything from data to newData that is after our offset
            final int remainingExistingCharacters = existingCharactersLen - (existingDataOffset + existingDataLen);
            System.arraycopy(document.characters, existingDataOffset + existingDataLen, newCharacters, existingDataOffset + len, remainingExistingCharacters);

            // 5) replace document.characters with our new characters
            document.characters = newCharacters;
            document.alphaLen[nodeNumber] = len;


            // 6) renumber all offsets following our offset
            for(int i = nodeNumber + 1; i < document.alpha.length; i++) {
                document.alpha[i] += extraRequired;
            }
        }
    }

    @Override
    public void deleteData(final int offset, int count) throws DOMException {
        replaceData(offset, count, "");
    }

    @Override
    public String getNodeValue() throws DOMException {
        return getData();
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
        setData(nodeValue);
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
    public String getStringValue() {
        return getData();
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result)
        throws XPathException {
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
    }
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.indexing.lucene;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.BytesRef;

/**
 * A binary tokenstream that lets you index a single
 * binary token (BytesRef value).
 *
 */
public final class BinaryTokenStream extends TokenStream {
    private final ByteTermAttribute bytesAtt = addAttribute(ByteTermAttribute.class);
    private boolean available = true;

    public BinaryTokenStream(BytesRef bytes) {
        bytesAtt.setBytesRef(bytes);
    }

    @Override
    public boolean incrementToken() {
        if (available) {
            available = false;
            return true;
        }
        return false;
    }

    @Override
    public void reset() {
        available = true;
    }

    public interface ByteTermAttribute extends TermToBytesRefAttribute {
        public void setBytesRef(BytesRef bytes);
    }

    public static class ByteTermAttributeImpl extends AttributeImpl implements ByteTermAttribute,TermToBytesRefAttribute {
        private BytesRef bytes;

        @Override
        public void fillBytesRef() {
        }

        @Override
        public BytesRef getBytesRef() {
            return bytes;
        }

        @Override
        public void setBytesRef(BytesRef bytes) {
            this.bytes = bytes;
        }

        @Override
        public void clear() {}

        @Override
        public void copyTo(AttributeImpl target) {
            ByteTermAttributeImpl other = (ByteTermAttributeImpl) target;
            other.bytes = bytes;
        }
    }
}


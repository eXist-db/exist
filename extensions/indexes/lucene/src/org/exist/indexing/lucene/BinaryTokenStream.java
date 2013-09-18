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
        public int fillBytesRef() {
            return bytes.hashCode();
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


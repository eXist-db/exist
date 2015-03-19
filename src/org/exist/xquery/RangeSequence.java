package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.value.AbstractSequence;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

public class RangeSequence extends AbstractSequence {

	private final static Logger LOG = LogManager.getLogger(AbstractSequence.class);
	
	private IntegerValue start;
	private IntegerValue end;
	
	public RangeSequence(IntegerValue start, IntegerValue end) {
		this.start = start;
		this.end = end;
	}

	public void add(Item item) throws XPathException {
		throw new XPathException("Internal error: adding to an immutable sequence");
	}

	public void addAll(Sequence other) throws XPathException {
		throw new XPathException("Internal error: adding to an immutable sequence");
	}

	public int getItemType() {
		return Type.INTEGER;
	}

    @Override
	public SequenceIterator iterate() throws XPathException {
		return new RangeSequenceIterator(start.getLong(), end.getLong());
	}

    @Override
	public SequenceIterator unorderedIterator() throws XPathException {
		return new RangeSequenceIterator(start.getLong(), end.getLong());
	}

    public SequenceIterator iterateInReverse() throws XPathException {
        return new ReverseRangeSequenceIterator(start.getLong(), end.getLong());
    }

	private static class RangeSequenceIterator implements SequenceIterator {
		private long current;
        private final long end;

		public RangeSequenceIterator(final long start, final long end) {
			this.current = start;
            this.end = end;
		}

		public Item nextItem() {
            if (current <= end) {
                return new IntegerValue(current++);
            } else {
                return null;
            }
		}

		public boolean hasNext() {
            return current <= end;
		}
	}

    private static class ReverseRangeSequenceIterator implements SequenceIterator {
        private final long start;
        private long current;

        public ReverseRangeSequenceIterator(final long start, final long end) {
            this.start = start;
            this.current = end;
        }

        public Item nextItem() {
            if (current >= start) {
                return new IntegerValue(current--);
            } else {
                return null;
            }
        }

        public boolean hasNext() {
            return current >= start;
        }
    }
	
	public int getItemCount() {
		if (start.compareTo(end) > 0)
			{return 0;}
		try {
			return ((IntegerValue) end.minus(start)).getInt() + 1;
		} catch (final XPathException e) {
			LOG.warn("Unexpected exception when processing result of range expression: " + e.getMessage(), e);
			return 0;
		}
	}

	public boolean isEmpty() {
		return getItemCount() == 0;
	}

	public boolean hasOne() {
		return getItemCount() == 1;
	}

	public boolean hasMany() {
		return getItemCount() > 1;
	}

	public Item itemAt(int pos) {
		if (pos <= getItemCount())
			try {
				return new IntegerValue(start.getLong() + pos);
			} catch (final XPathException e) {
				LOG.warn("Unexpected exception when processing result of range expression: " + e.getMessage(), e);
			}
		return null;
	}

	public NodeSet toNodeSet() throws XPathException {
		throw new XPathException("Type error: the sequence cannot be converted into" +
				" a node set. Item type is xs:integer");
	}

	public MemoryNodeSet toMemNodeSet() throws XPathException {
		throw new XPathException("Type error: the sequence cannot be converted into" +
			" a node set. Item type is xs:integer");
	}

	public void removeDuplicates() {
	}
}

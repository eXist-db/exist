package org.exist.xquery;

import org.apache.log4j.Logger;
import org.exist.dom.NodeSet;
import org.exist.xquery.value.AbstractSequence;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

public class RangeSequence extends AbstractSequence {

	private final static Logger LOG = Logger.getLogger(AbstractSequence.class);
	
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

	public SequenceIterator iterate() throws XPathException {
		return new RangeSequenceIterator(start.getLong());
	}

	public SequenceIterator unorderedIterator() throws XPathException {
		return new RangeSequenceIterator(start.getLong());
	}

	private class RangeSequenceIterator implements SequenceIterator {

		long current;

		public RangeSequenceIterator(long start) {
			this.current = start;
		}

		public Item nextItem() {
			try {
				if (current <= end.getLong())
					{return new IntegerValue(current++);}
			} catch (final XPathException e) {
				LOG.warn("Unexpected exception when processing result of range expression: " + e.getMessage(), e);
			}
			return null;
		}

		public boolean hasNext() {
			try {
				return current <= end.getLong();
			} catch (final XPathException e) {
				LOG.warn("Unexpected exception when processing result of range expression: " + e.getMessage(), e);
				return false;
			}
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

package org.exist.dom;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.exist.util.FastQSort;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;

public class TextSearchResult extends AbstractNodeSet {

	public final static int INITIAL_ARRAY_SIZE = 250;

	private Map documents = new TreeMap();
	private int size = 0;
	private boolean trackMatches = true;

	public TextSearchResult(boolean trackMatches) {
		this.trackMatches = trackMatches;
	}

	public int getLength() {
		return size;
	}

	public void add(DocumentImpl doc, long gid, String matchString) {
		Entry entry = (Entry) documents.get(doc);
		if (entry == null) {
			entry = new Entry(trackMatches);
			documents.put(doc, entry);
		}
		entry.add(gid, matchString);
		++size;
	}

	public void getDocuments(DocumentSet allDocs) {
		for (Iterator i = documents.keySet().iterator(); i.hasNext();) {
			allDocs.add((DocumentImpl) i.next());
		}
	}

	public NodeSet process(NodeSet contextSet) {
		ArraySet result =
			contextSet instanceof VirtualNodeSet
				? new ArraySet(100)
				: new ArraySet(contextSet.getLength());
		Map.Entry next;
		Entry entry;
		for (Iterator i = documents.entrySet().iterator(); i.hasNext();) {
			next = (Map.Entry) i.next();
			entry = (Entry) next.getValue();
			entry.sort();
			entry.process((DocumentImpl) next.getKey(), result, contextSet);
		}
		return result;
	}

	public NodeSet asNodeSet(NodeSet contextSet) {
		ArraySet result =
			contextSet instanceof VirtualNodeSet
				? new ArraySet(100)
				: new ArraySet(contextSet.getLength());
		Map.Entry next;
		Entry entry;
		for (Iterator i = documents.entrySet().iterator(); i.hasNext();) {
			next = (Map.Entry) i.next();
			entry = (Entry) next.getValue();
			entry.sort();
			entry.copyTo((DocumentImpl) next.getKey(), result, contextSet);
		}
		return result;
	}

	private final static class Entry {

		long nodes[];
		int position = -1;
		String matched[] = null;

		public Entry(boolean trackMatches) {
			nodes = new long[INITIAL_ARRAY_SIZE];
			if (trackMatches)
				matched = new String[INITIAL_ARRAY_SIZE];
		}

		public void add(long gid, String matchString) {
			if (++position == nodes.length) {
				long n[] = new long[nodes.length << 1];
				System.arraycopy(nodes, 0, n, 0, nodes.length);
				nodes = n;

				if (matched != null) {
					String[] m = new String[matched.length << 1];
					System.arraycopy(matched, 0, m, 0, matched.length);
					matched = m;
				}
			}
			nodes[position] = gid;
			if (matched != null)
				matched[position] = matchString;
		}

		public void sort() {
			FastQSort.sort(nodes, 0, position, matched);
		}

		public void process(
			DocumentImpl doc,
			NodeSet result,
			NodeSet contextSet) {
			NodeProxy parent, p;
			for (int i = 0; i <= position; i++) {
				parent = XMLUtil.parentWithChild(contextSet, doc, nodes[i], -1);
				if (parent != null) {
					if (matched != null)
						parent.addMatch(new Match(matched[i], nodes[i]));
					if (!result.contains(parent))
						result.add(parent);
				}
			}
		}

		public void copyTo(
			DocumentImpl doc,
			NodeSet result,
			NodeSet contextSet) {
			NodeProxy p;
			for (int i = 0; i <= position; i++) {
				p = contextSet.get(doc, nodes[i]);
				if (p != null) {
					if (matched != null)
						p.addMatch(new Match(matched[i], nodes[i]));
					if (!result.contains(p))
						result.add(p);
				}
			}
		}
	}

	/* NodeSet methods. These methods are just required for compatibility. 
	 * They just fail silently. 
	 */
	
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterator()
	 */
	public Iterator iterator() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.DocumentImpl, long)
	 */
	public boolean contains(DocumentImpl doc, long nodeId) {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
	 */
	public boolean contains(NodeProxy proxy) {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
	 */
	public void addAll(NodeSet other) {
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#item(int)
	 */
	public Node item(int pos) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(int)
	 */
	public NodeProxy get(int pos) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
	 */
	public NodeProxy get(NodeProxy p) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.DocumentImpl, long)
	 */
	public NodeProxy get(DocumentImpl doc, long nodeId) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy)
	 */
	public void add(NodeProxy proxy) {
	}
}

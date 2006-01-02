package org.exist.dom;

/**
 * This interface is used to report changes of the node id or the storage address
 * of a node to classes which have to keep node sets up to date during processing.
 * Used by the XUpdate classes to update the query result sets.
 * 
 * @author wolf
 */
public interface NodeIndexListener {

	/**
	 * The internal id of a node has changed. The storage address is
	 * still the same, so one can find the changed node by comparing
	 * its storage address.
	 * 
	 * @param node
	 */
	void nodeChanged(StoredNode node);
}

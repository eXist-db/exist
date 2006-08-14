/*  $Id$ */

package org.exist.dom;

/** Applies Null Object Design Pattern
 * @author Jean-Marc Vanel - http:///jmvanel.free.fr */
public class NullNodeIndexListener implements NodeIndexListener {
	/** Singleton */
	public static final NodeIndexListener INSTANCE = new NullNodeIndexListener();
	/** @see org.exist.dom.NodeIndexListener#nodeChanged(StoredNode) */
	public void nodeChanged(StoredNode node) {	}
	public void nodeChanged(long oldAddress, long newAddress) {	}
}

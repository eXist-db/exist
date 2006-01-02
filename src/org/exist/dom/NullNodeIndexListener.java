/*  $Id$ */

package org.exist.dom;

/** Applies Null Object Design Pattern
 * @author Jean-Marc Vanel - http:///jmvanel.free.fr */
public class NullNodeIndexListener implements NodeIndexListener {
	/** Singleton */
	public static final NodeIndexListener INSTANCE = new NullNodeIndexListener();
	/** @see org.exist.dom.NodeIndexListener#nodeChanged(org.exist.dom.NodeImpl) */
	public void nodeChanged(StoredNode node) {	}
	/** @see org.exist.dom.NodeIndexListener#nodeChanged(long, long) */
	public void nodeChanged(long oldAddress, long newAddress) {	}
}
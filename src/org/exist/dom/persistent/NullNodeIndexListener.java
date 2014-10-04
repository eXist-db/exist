/*  $Id$ */

package org.exist.dom.persistent;

/** Applies Null Object Design Pattern
 * @author Jean-Marc Vanel - http:///jmvanel.free.fr */
public class NullNodeIndexListener implements NodeIndexListener {

    /** Singleton */
    public static final NodeIndexListener INSTANCE = new NullNodeIndexListener();

    /** @see org.exist.dom.persistent.NodeIndexListener#nodeChanged(StoredNode) */
    @Override
    public void nodeChanged(NodeHandle node) {	}
}

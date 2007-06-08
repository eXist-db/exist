package org.exist.util.pool;

import org.junit.Test;
import org.w3c.dom.Node;
import org.exist.dom.NodeImpl;
import static junit.framework.Assert.*;

public class NodePoolTest {

    /**
     * Make sure the NodePool doesn't grow beyond its bounds.
     */
    @Test
    public void testPool() {
        NodePool pool = NodePool.getInstance();
        NodeImpl nodes[] = new NodeImpl[100];

        for (int i = 0; i < 100; i++) {
            nodes[i] = pool.borrowNode(Node.ELEMENT_NODE);
        }
        for (int i = 99; i > -1; i--) {
            pool.returnNode(nodes[i]);
        }
        assertEquals(NodePool.MAX_OBJECTS, pool.getSize(Node.ELEMENT_NODE));
    }
}
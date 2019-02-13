package org.exist.indexing.range;

import org.exist.xquery.*;
import org.exist.xquery.modules.range.Lookup;
import org.w3c.dom.Node;


/**
 *
 * Base class for conditions that can be defined for complex range config elements.
 *
 * @author Marcel Schaeben
 */
public abstract class RangeIndexConfigCondition {


    /**
     * Test if a node matches this condition. Used by the indexer.
     * @param node The node to test.
     * @return true if the node is an element node and an attribute matches this condition.
     */
    public abstract boolean matches(Node node);

    /**
     * Test if an expression defined by the arguments matches this condition. Used by the query rewriter.
     * @param predicate The predicate to test.
     * @return true if the predicate matches this condition.
     */
    public boolean find(Predicate predicate) {

        return false;
    }

    /**
     * Get the inner expression of a predicate. Will unwrap the original expression if it has previously
     * been rewritten into an index function call.
     * @param predicate The predicate to test.
     * @return The fallback expression from a rewritten function call or the original inner expression.
     */
    protected Expression getInnerExpression(Predicate predicate) {
        Expression inner = predicate.getExpression(0);
        if (inner instanceof InternalFunctionCall) {
            Function function = ((InternalFunctionCall)inner).getFunction();
            if (function instanceof Lookup) {
                return ((Lookup)function).getFallback();
            }
        }

        return inner;
    }

}
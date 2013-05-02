package org.exist.xquery;

/**
 * Base class to be implemented by an index module if it wants to rewrite
 * certain query expressions. Subclasses should overwrite the rewriteXXX methods
 * they are interested in.
 *
 * @author Wolfgang Meier
 */
public class QueryRewriter {

    private final XQueryContext context;

    public QueryRewriter(XQueryContext context) {
        this.context = context;
    }

    public boolean rewriteLocationStep(LocationStep locationStep) throws XPathException {
        return false;
    }

    protected XQueryContext getContext() {
        return context;
    }
}

package org.exist.xquery.modules.ngram.utils;

/**
 * A transformation or function from <code>A</code> to <code>B</code>.
 */
public interface F<A, B> {
    /**
     * Transform <code>A</code> to <code>B</code>.
     * 
     * @param a
     *            The <code>A</code> to transform.
     * @return The result of the transformation.
     */
    B f(A a);
}

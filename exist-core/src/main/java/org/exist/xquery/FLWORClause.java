package org.exist.xquery;

import org.exist.xquery.value.Sequence;

/**
 * Interface for FLWOR clauses like for/let/group by ...
 *
 * @author wolf
 */
public interface FLWORClause extends Expression {

    enum ClauseType {
        FOR, LET, GROUPBY, ORDERBY, WHERE, SOME, EVERY
    }

    /**
     * Returns the type of clause implemented by a subclass.
     *
     * @return the type of the clause
     */
    ClauseType getType();

    /**
     * Set the return expression of the clause. Might either be
     * an expression given in a "return" or another clause.
     *
     * @param expr the return expression
     */
    void setReturnExpression(Expression expr);

    /**
     * Get the return expression of the clause.
     *
     * @return the return expression
     */
    Expression getReturnExpression();

    /**
     * Set the previous FLWOR clause if this is not the
     * top clause.
     *
     * @param clause the previous clause
     */
    void setPreviousClause(FLWORClause clause);

    /**
     * Get the previous FLWOR clause if this is not the
     * top clause.
     *
     * @return previous clause or null if this is the top clause
     */
    FLWORClause getPreviousClause();

    /**
     * Called by a for clause before it starts iteration, passing in
     * the sequence of items to be iterated. Used by {@link WhereClause}
     * to filter the input sequence in advance if possible.
     *
     * @param seq the sequence of items to be iterated by the current for
     * @return post-processed result sequence
     * @throws XPathException if an error occurs during pre-evaluation
     */
    Sequence preEval(Sequence seq) throws XPathException;

    /**
     * Called by the top FLWOR expression when it finished iteration.
     * Implemented by {@link GroupByClause}, which first collects
     * tuples into groups, then processes them in this method.
     *
     * @param seq the return sequence of the top FLWOR expression
     * @return post-processed result sequence
     * @throws XPathException if an error occurs during post-evaluation
     */
    Sequence postEval(Sequence seq) throws XPathException;

    /**
     * Create a new local variable for the FLWOR clause.
     * Tracks the variables for this expression.
     *
     * @param name the name of the variable
     * @return a new local variable, registered in the context
     * @throws XPathException if an error occurs whilst creating the variable
     */
    LocalVariable createVariable(String name) throws XPathException;

    /**
     * Returns the first variable created by this FLWOR clause for reference
     * from subsequent clauses.
     *
     * @return first variable created
     */
    LocalVariable getStartVariable();
}
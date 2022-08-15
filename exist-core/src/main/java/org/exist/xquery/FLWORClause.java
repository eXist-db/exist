/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

import java.util.Set;

/**
 * Interface for FLWOR clauses like for/let/group by ...
 *
 * @author wolf
 */
public interface FLWORClause extends Expression {

    enum ClauseType {
        FOR, LET, GROUPBY, ORDERBY, WHERE, SOME, EVERY, COUNT, WINDOW
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
    LocalVariable createVariable(QName name) throws XPathException;

    /**
     * Returns the first variable created by this FLWOR clause for reference
     * from subsequent clauses.
     *
     * @return first variable created
     */
    LocalVariable getStartVariable();

    /**
     * Get the list of variables constructed in the tuple stream.
     *
     * @return the list of variables constructed in the tuple stream.
     */
    Set<QName> getTupleStreamVariables();
}

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

import org.exist.dom.persistent.DocumentSet;
import org.exist.source.Source;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;

/**
 * Base interface implemented by all classes which are part
 * of an XQuery/XPath expression.
 */
public interface Expression extends Materializable {

    // Flags to be passed to analyze:
    /**
     * Indicates that the query engine will call the expression once for every
     * item in the context sequence. This is what you would expect to be the
     * normal behaviour of an XQuery processor. However, eXist tries to process
     * some types of expressions in one single step for the whole input sequence.
     * So if the flag is not set, the expression is only called once.
     */
    public final static int SINGLE_STEP_EXECUTION = 1;

    /**
     * Indicates that the expression is within a predicate or the where clause of
     * a FLWOR.
     */
    public final static int IN_PREDICATE = 2;

    /**
     * Indicates that the expression is within a where clause of a FLWOR. This
     * flag will be set in addition to {@link #IN_PREDICATE}.
     */
    public final static int IN_WHERE_CLAUSE = 4;

    /**
     * Indicates that the expression is used within an update statement. Subexpressions
     * should not cache any relevant data as it may be subject to change.
     */
    public final static int IN_UPDATE = 8;
    public final static int NEED_INDEX_INFO = 16;
    public final static int USE_TREE_TRAVERSAL = 32;
    public final static int POSITIONAL_PREDICATE = 64;
    public final static int DOT_TEST = 128;
    public final static int IN_NODE_CONSTRUCTOR = 256;

    /**
     * Indicates that the expression will redirect subexpressions evaluation 
     * result to output stream after some manipulations.
     */
    public final static int NON_STREAMABLE = 512;

    /**
     * Indicates that sequence .
     */
    public final static int UNORDERED = 1024;

    /**
     * Indicates that no context id is supplied to an expression.
     */
    public final static int NO_CONTEXT_ID = -1;
    public final static int IGNORE_CONTEXT = -2;

    /**
     * Marks an invalid expression id.
     */
    public final static int EXPRESSION_ID_INVALID = -1;

    /**
     * Returns an id which uniquely identifies this expression
     * within the compiled expression tree of the query.
     * 
     * @return unique id or {@link #EXPRESSION_ID_INVALID}
     */
    public int getExpressionId();

    /**
     * Statically analyze the expression and its subexpressions.
     *
     * During the static analysis phase, the query engine can detect
     * unknown variables and some type errors.
     *
     * @param contextInfo the context infomation.
     *
     * @throws XPathException if an error occurs during the analysis.
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException;

    public void setPrimaryAxis(int axis);

    public int getPrimaryAxis();

    /**
     * The static return type of the expression.
     *
     * This method should return one of the type constants defined in class
     * {@link org.exist.xquery.value.Type}. If the return type cannot be determined
     * statically, return Type.ITEM.
     *
     * @return the type.
     */
    public int returnsType();

    /**
     * The expected cardinality of the return value of the expression.
     *
     * Should return a bit mask with bits set as defined in class {@link Cardinality}.
     *
     * @return the cardinality.
     */
    public Cardinality getCardinality();

    /**
     * Returns a set of bit-flags, indicating some of the parameters
     * on which this expression depends. The flags are defined in
     * {@link Dependency}.
     *
     * @return set of bit-flags
     */
    public int getDependencies();

    public Expression simplify();

    /**
     * Called to inform an expression that it should reset to its initial state.
     *
     * All cached data in the expression object should be dropped. For example,
     * the xmldb:document() function calls this method whenever the input document
     * set has changed.
     *
     * @param postOptimization true if post optimisation should be undertaken.
     */
    public void resetState(boolean postOptimization);

    /**
     * Returns true if the expression object has not yet been reset, so
     * {@link #resetState(boolean)} should be called.
     *
     * @return true if the expression needs reseting.
     */
    public boolean needsReset();

    /**
     * Start traversing the expression tree using the specified {@link ExpressionVisitor}.
     *
     * @param visitor the visitor
     */
    public void accept(ExpressionVisitor visitor);

    /**
     * Write a diagnostic dump of the expression to the passed
     * {@link ExpressionDumper}.
     *
     * @param dumper the expression dumper to write to
     */
    public void dump(ExpressionDumper dumper);

    public void setContextDocSet(DocumentSet contextSet);

    public void setContextId(int contextId);

    public int getContextId();

    public DocumentSet getContextDocSet();

    public void setASTNode(XQueryAST ast);

    public void setLocation(int line, int column);

    public int getLine();

    public int getColumn();

    public XQueryContext getContext();

    public Source getSource();

    //Expression is the part of tree, next methods allow to walk down the tree
    public int getSubExpressionCount();

    public Expression getSubExpression(int index);

    public boolean allowMixedNodesInReturn();

    public Expression getParent();

    /**
     * Return true only if the next expression within a path expression
     * should be evaluated even when this expression returns an
     * empty sequence.
     *
     * @return true if the next expression should be evaluated, false otherwise.
     */
    boolean evalNextExpressionOnEmptyContextSequence();
}
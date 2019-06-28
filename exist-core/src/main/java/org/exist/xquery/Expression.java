/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2010 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.source.Source;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Base interface implemented by all classes which are part
 * of an XQuery/XPath expression. The main method is 
 * {@link #eval(Sequence, Item)}. Please
 * read the description there.
 */
public interface Expression {

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

    /**
     * Evaluate the expression represented by this object.
     *
     * Depending on the context in which this expression is executed,
     * either the context sequence, the context item or both of them may
     * be set. An implementing class should know how to handle this.
     *
     * The general contract is as follows: if the {@link Dependency#CONTEXT_ITEM}
     * bit is set in the bit field returned by {@link #getDependencies()}, the eval method will
     * be called once for every item in the context sequence. The <b>contextItem</b>
     * parameter will be set to the current item. Otherwise, the eval method will only be called
     * once for the whole context sequence and <b>contextItem</b> will be null.
     *
     * eXist tries to process the entire context set in one, single step whenever
     * possible. Thus, most classes only expect context to contain a list of
     * nodes which represents the current context of the expression.
     *
     * The position() function in XPath is an example for an expression,
     * which requires both, context sequence and context item to be set.
     *
     * The context sequence might be a node set, a sequence of atomic values or a single
     * node or atomic value.
     *
     * @param contextSequence the current context sequence.
     * @param contextItem a single item, taken from context. This defines the item,
     * the expression should work on.
     *
     * @return the result sequence.
     *
     * @throws XPathException if an error occurs during evaluation.
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException;

    /**
     * Evaluate the expression represented by this object.
     *
     * An overloaded method which just passes the context sequence depending on the
     * expression context.
     *
     * @param contextSequence the current context sequence.
     *
     * @return the result sequence.
     *
     * @throws XPathException if an error occurs during evaluation.
     */
    public Sequence eval(Sequence contextSequence) throws XPathException;

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
    public int getCardinality();

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

    //check will item process by the expression 
    public Boolean match(Sequence contextSequence, Item item) throws XPathException;

    public boolean allowMixedNodesInReturn();

    public Expression getParent();
}
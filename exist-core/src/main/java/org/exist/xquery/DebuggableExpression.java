/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009-2013 The eXist-db Project
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
 *  $Id$
 */
package org.exist.xquery;

import org.exist.source.Source;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Item;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.parser.XQueryAST;
import org.exist.dom.persistent.DocumentSet;

public class DebuggableExpression implements Expression, RewritableExpression {

    private Expression expression;

    protected int line = -1;
    protected int column = -1;

    public DebuggableExpression(Expression expression) {
        this.expression = expression.simplify();
        this.line = this.expression.getLine();
        this.column = this.expression.getColumn();
    }

    public int getExpressionId() {
        return expression.getExpressionId();
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        if (contextInfo.getParent() == null) {
            contextInfo.setParent(this);
        }
        expression.analyze(contextInfo);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        try {
            expression.getContext().expressionStart(expression);
            return expression.eval(contextSequence, contextItem);
        } finally {
            expression.getContext().expressionEnd(expression);
        }
    }

    public Sequence eval(Sequence contextSequence) throws XPathException {
        return eval(contextSequence, null);
    }

    public void setPrimaryAxis(int axis) {
        expression.setPrimaryAxis(axis);
    }

    public int getPrimaryAxis() {
        return expression.getPrimaryAxis();
    }

    public int returnsType() {
        return expression.returnsType();
    }

    public int getCardinality() {
        return expression.getCardinality();
    }

    public int getDependencies() {
        return expression.getDependencies();
    }

    public void resetState(boolean postOptimization) {
        expression.resetState(postOptimization);
    }

    public boolean needsReset() {
    	return true;
    }

    public void accept(ExpressionVisitor visitor) {
        expression.accept(visitor);
    }

    public void dump(ExpressionDumper dumper) {
        expression.dump(dumper);
    }

    public void setContextDocSet(DocumentSet contextSet) {
        expression.setContextDocSet(contextSet);
    }

    public void setContextId(int contextId) {
        expression.setContextId(contextId);
    }

    public int getContextId() {
        return expression.getContextId();
    }

    public DocumentSet getContextDocSet() {
        return expression.getContextDocSet();
    }

   public void setASTNode(XQueryAST ast) {
        if (ast != null) {
            line = ast.getLine();
            column = ast.getColumn();
        }
   }

    public void setLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public XQueryContext getContext() {
        return expression.getContext();
    }

    public Source getSource() {
        return expression.getSource();
    }

    public int getSubExpressionCount() {
        return expression.getSubExpressionCount();
    }

    public Expression getSubExpression(int index) {
        return expression.getSubExpression(index);
    }

    public Boolean match(Sequence contextSequence, Item item) throws XPathException {
        return expression.match(contextSequence, item);
    }

    @Override
    public void replace(Expression oldExpr, Expression newExpr) {
        if (oldExpr == expression)
            {expression = newExpr;}
    }

    @Override
    public void remove(Expression oldExpr) throws XPathException {
        throw new XPathException("Method remove is not supported");
    }

    @Override
    public Expression getPrevious(Expression current) {
        return null;
    }

    @Override
    public Expression getFirst() {
        return expression;
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return false;
    }

    @Override
    public String toString() {
        return expression.toString();
    }

    @Override
    public Expression simplify() {
        return this;
    }

    @Override
    public Expression getParent() {
        return null;
    }
}

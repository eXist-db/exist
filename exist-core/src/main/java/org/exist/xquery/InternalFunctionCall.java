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

import java.io.Writer;
import java.util.List;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Wrapper for internal modules in order to
 * perform access control checks on internal
 * module function calls.  It delegates to
 * the wrapped <code>Function</code> for
 * everything, but checks permission before
 * delegating <code>eval</code>
 */
public class InternalFunctionCall extends Function {
    private final Function function;

    public InternalFunctionCall(final Function f) {
        super(f.getContext(), f.getSignature());
        this.function = f;
        this.parentModule = f.parentModule;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        context.proceed(this);

        final long start = System.currentTimeMillis();
        if (context.getProfiler().traceFunctions()) {
            context.getProfiler().traceFunctionStart(this);
        }

        context.stackEnter(this);
        try {
            return function.eval(contextSequence, contextItem);
        } catch (final XPathException e) {
            if (e.getLine() <= 0) {
                e.setLocation(line, column, getSource());
            }
            throw e;
        } finally {
            context.stackLeave(this);

            if (context.getProfiler().traceFunctions()) {
                context.getProfiler().traceFunctionEnd(this, System.currentTimeMillis() - start);
            }
        }
    }

    public Function getFunction() {
        return function;
    }

    @Override
    public int getArgumentCount() {
        return function.getArgumentCount();
    }

    @Override
    public QName getName() {
        return function.getName();
    }

    @Override
    public int returnsType() {
        return function.returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        return function.getCardinality();
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        try {
            function.analyze(contextInfo);
        } catch (final XPathException e) {
            if (e.getLine() <= 0) {
                e.setLocation(line, column, getSource());
            }
            throw e;
        }
    }

    @Override
    public void setParent(final Expression parent) {
        function.setParent(parent);
    }

    @Override
    public Expression getParent() {
        return function.getParent();
    }

    @Override
    public XQueryContext getContext() {
        return function.getContext();
    }

    @Override
    public int getLine() {
        return function.getLine();
    }

    @Override
    public int getColumn() {
        return function.getColumn();
    }

    @Override
    public void setASTNode(final XQueryAST ast) {
        function.setASTNode(ast);
    }

    @Override
    public void setLocation(final int line, final int column) {
        function.setLocation(line, column);
    }

    @Override
    public void add(final Expression s) {
        function.add(s);
    }

    @Override
    public void add(final PathExpr path) {
        function.add(path);
    }

    @Override
    public void addPath(final PathExpr path) {
        function.addPath(path);
    }

    @Override
    public void addPredicate(final Predicate pred) {
        function.addPredicate(pred);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        function.dump(dumper);
    }

    @Override
    public void dump(final Writer writer) {
        function.dump(writer);
    }

    @Override
    public Expression getArgument(final int pos) {
        return function.getArgument(pos);
    }

    @Override
    public Sequence[] getArguments(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return function.getArguments(contextSequence, contextItem);
    }

    @Override
    public DocumentSet getContextDocSet() {
        return function.getContextDocSet();
    }

    @Override
    public int getDependencies() {
        return function.getDependencies();
    }

    @Override
    public DocumentSet getDocumentSet() {
        return function.getDocumentSet();
    }

    @Override
    public Expression getExpression(final int pos) {
        return function.getExpression(pos);
    }

    @Override
    public Expression getLastExpression() {
        return function.getLastExpression();
    }

    @Override
    public int getLength() {
        return function.getLength();
    }

    @Override
    public String getLiteralValue() {
        return function.getLiteralValue();
    }

    @Override
    public FunctionSignature getSignature() {
        return function.getSignature();
    }

    @Override
    public boolean isCalledAs(final String localName) {
        return function.isCalledAs(localName);
    }

    @Override
    public void replaceLastExpression(final Expression s) {
        function.replaceLastExpression(s);
    }

    @Override
    public void reset() {
        function.reset();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        function.resetState(postOptimization);
    }

    @Override
    public void setArguments(final List<Expression> arguments) throws XPathException {
        function.setArguments(arguments);
    }

    @Override
    public void setContext(final XQueryContext context) {
        function.setContext(context);
    }

    @Override
    public void setContextDocSet(final DocumentSet contextSet) {
        function.setContextDocSet(contextSet);
    }

    @Override
    public String toString() {
        return function.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        function.accept(visitor);
    }
}
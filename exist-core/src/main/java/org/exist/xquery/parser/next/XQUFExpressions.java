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
package org.exist.xquery.parser.next;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub XQUF expression classes for the hand-written parser prototype.
 *
 * <p>These classes mirror the constructors and structure of the real XQUF
 * expression classes in {@code org.exist.xquery.xquf.*} (available on the
 * {@code next} branch / post-7.0). They allow the parser to build correct
 * expression trees that will work when the real runtime is available.</p>
 *
 * <p>When integrating with post-7.0 eXist, replace these stubs with imports
 * from the real {@code org.exist.xquery.xquf} package.</p>
 */
public final class XQUFExpressions {

    private XQUFExpressions() {}

    /** copy $var := expr, ... modify expr return expr */
    public static class TransformExpr extends AbstractExpression {
        private final List<CopyBinding> copyBindings;
        private final Expression modifyExpr;
        private final Expression returnExpr;

        public TransformExpr(XQueryContext context, List<CopyBinding> copyBindings,
                             Expression modifyExpr, Expression returnExpr) {
            super(context);
            this.copyBindings = copyBindings;
            this.modifyExpr = modifyExpr;
            this.returnExpr = returnExpr;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQUF transform expression requires post-7.0 runtime");
        }

        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper dumper) { dumper.display("copy/modify/return"); }
        @Override public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {}
    }

    public static class CopyBinding {
        public final QName varName;
        public final Expression sourceExpr;
        public CopyBinding(QName varName, Expression sourceExpr) {
            this.varName = varName;
            this.sourceExpr = sourceExpr;
        }
    }

    /** insert node SOURCE into/before/after TARGET */
    public static class InsertExpr extends AbstractExpression {
        public static final int INSERT_INTO = 0;
        public static final int INSERT_INTO_AS_FIRST = 1;
        public static final int INSERT_INTO_AS_LAST = 2;
        public static final int INSERT_BEFORE = 3;
        public static final int INSERT_AFTER = 4;

        private final Expression source;
        private final Expression target;
        private final int mode;

        public InsertExpr(XQueryContext context, Expression source, Expression target, int mode) {
            super(context);
            this.source = source;
            this.target = target;
            this.mode = mode;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQUF insert expression requires post-7.0 runtime");
        }

        @Override public int returnsType() { return org.exist.xquery.value.Type.EMPTY_SEQUENCE; }
        @Override public void dump(ExpressionDumper dumper) { dumper.display("insert"); }
        @Override public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {}
    }

    /** delete node TARGET */
    public static class DeleteExpr extends AbstractExpression {
        private final Expression target;

        public DeleteExpr(XQueryContext context, Expression target) {
            super(context);
            this.target = target;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQUF delete expression requires post-7.0 runtime");
        }

        @Override public int returnsType() { return org.exist.xquery.value.Type.EMPTY_SEQUENCE; }
        @Override public void dump(ExpressionDumper dumper) { dumper.display("delete"); }
        @Override public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {}
    }

    /** replace node TARGET with REPLACEMENT */
    public static class ReplaceNodeExpr extends AbstractExpression {
        private final Expression target;
        private final Expression replacement;

        public ReplaceNodeExpr(XQueryContext context, Expression target, Expression replacement) {
            super(context);
            this.target = target;
            this.replacement = replacement;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQUF replace node expression requires post-7.0 runtime");
        }

        @Override public int returnsType() { return org.exist.xquery.value.Type.EMPTY_SEQUENCE; }
        @Override public void dump(ExpressionDumper dumper) { dumper.display("replace node"); }
        @Override public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {}
    }

    /** replace value of node TARGET with VALUE */
    public static class ReplaceValueExpr extends AbstractExpression {
        private final Expression target;
        private final Expression value;

        public ReplaceValueExpr(XQueryContext context, Expression target, Expression value) {
            super(context);
            this.target = target;
            this.value = value;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQUF replace value expression requires post-7.0 runtime");
        }

        @Override public int returnsType() { return org.exist.xquery.value.Type.EMPTY_SEQUENCE; }
        @Override public void dump(ExpressionDumper dumper) { dumper.display("replace value"); }
        @Override public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {}
    }

    /** rename node TARGET as NEWNAME */
    public static class RenameExpr extends AbstractExpression {
        private final Expression target;
        private final Expression newName;

        public RenameExpr(XQueryContext context, Expression target, Expression newName) {
            super(context);
            this.target = target;
            this.newName = newName;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQUF rename expression requires post-7.0 runtime");
        }

        @Override public int returnsType() { return org.exist.xquery.value.Type.EMPTY_SEQUENCE; }
        @Override public void dump(ExpressionDumper dumper) { dumper.display("rename"); }
        @Override public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {}
    }
}

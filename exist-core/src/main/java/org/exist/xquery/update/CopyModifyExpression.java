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
package org.exist.xquery.update;

import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class CopyModifyExpression extends PathExpr {

    public static class CopySource {
        private String varName;
        private Expression inputSequence;

        public String getVariable() {
            return this.varName;
        }

        public Expression getInputSequence() {
            return this.inputSequence;
        }

        public void setVariable(final String varName) {
            this.varName = varName;
        }

        public void setInputSequence(final Expression inputSequence) {
            this.inputSequence = inputSequence;
        }

        public CopySource(final String name, final Expression value) {
            this.varName = name;
            this.inputSequence = value;
        }

        public CopySource() {
        }
    }


    private List<CopySource> sources;
    private Expression modifyExpr;
    private Expression returnExpr;

    // see https://www.w3.org/TR/xquery-update-30/#id-copy-modify for details
    public Category getCategory() {
        // placeholder implementation
        return Category.SIMPLE;
    }

    public CopyModifyExpression(final XQueryContext context) {
        super(context);
        this.sources = new ArrayList<>();
    }

    public void addCopySource(final String varName, final Expression value) {
        this.sources.add(new CopySource(varName, value));
    }

    public void setModifyExpr(final Expression expr) {
        this.modifyExpr = expr;
    }

    public Expression getModifyExpr() {
        return this.modifyExpr;
    }

    public void setReturnExpr(final Expression expr) {
        this.returnExpr = expr;
    }

    public Expression getReturnExpr() {
        return this.returnExpr;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ONE_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("copy").nl();
        dumper.startIndent();
        for (int i = 0; i < sources.size(); i++) {
            dumper.display("$").display(sources.get(i).varName);
            dumper.display(" := ");
            sources.get(i).inputSequence.dump(dumper);
        }
        dumper.endIndent();
        dumper.display("modify").nl();
        modifyExpr.dump(dumper);
        dumper.nl().display("return ");
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("copy ");
        for (int i = 0; i < sources.size(); i++) {
            result.append("$").append(sources.get(i).varName);
            result.append(sources.get(i).inputSequence.toString());
            if (sources.size() > 1 && i < sources.size() - 1) {
                result.append(", ");
            } else {
                result.append(" ");
            }
        }
        result.append(" ");
        result.append("modify ");
        result.append(modifyExpr.toString());
        result.append(" ");
        result.append("return ");
        result.append(returnExpr.toString());
        return result.toString();
    }
}

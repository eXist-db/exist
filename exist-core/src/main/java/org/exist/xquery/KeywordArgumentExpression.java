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

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Wraps a function argument expression with a keyword name for XQuery 4.0
 * keyword argument syntax: {@code fn:slice($input, start := 3)}.
 *
 * <p>This is a transient wrapper used during function call construction.
 * The keyword name is used to match the argument to the correct parameter
 * position in the function signature.</p>
 */
public class KeywordArgumentExpression extends AbstractExpression {

    private final String keywordName;
    private final Expression argument;

    public KeywordArgumentExpression(final XQueryContext context, final String keywordName,
            final Expression argument) {
        super(context);
        this.keywordName = keywordName;
        this.argument = argument;
    }

    public String getKeywordName() {
        return keywordName;
    }

    public Expression getArgument() {
        return argument;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem)
            throws XPathException {
        return argument.eval(contextSequence, contextItem);
    }

    @Override
    public int returnsType() {
        return argument.returnsType();
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        argument.analyze(contextInfo);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display(keywordName);
        dumper.display(" := ");
        argument.dump(dumper);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        argument.resetState(postOptimization);
    }
}

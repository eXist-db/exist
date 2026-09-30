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
package org.exist.xquery.ft;

import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;

/**
 * W3C XQFT 3.0 — FTPrimaryWithOptions.
 *
 * <pre>FTPrimaryWithOptions ::= FTPrimary FTMatchOptions? FTWeight?</pre>
 */
public class FTPrimaryWithOptions extends FTAbstractExpr {

    private Expression primary;
    private FTMatchOptions matchOptions;
    private Expression weight;

    public FTPrimaryWithOptions(final XQueryContext context) {
        super(context);
    }

    public void setPrimary(final Expression primary) {
        this.primary = primary;
    }

    public Expression getPrimary() {
        return primary;
    }

    public void setMatchOptions(final FTMatchOptions matchOptions) {
        this.matchOptions = matchOptions;
    }

    public FTMatchOptions getMatchOptions() {
        return matchOptions;
    }

    public void setWeight(final Expression weight) {
        this.weight = weight;
    }

    public Expression getWeight() {
        return weight;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        primary.analyze(contextInfo);
        if (weight != null) {
            weight.analyze(contextInfo);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        primary.dump(dumper);
        if (matchOptions != null) {
            matchOptions.dump(dumper);
        }
        if (weight != null) {
            dumper.display(" weight { ");
            weight.dump(dumper);
            dumper.display(" }");
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(primary.toString());
        if (matchOptions != null) {
            sb.append(matchOptions.toString());
        }
        if (weight != null) {
            sb.append(" weight { ").append(weight.toString()).append(" }");
        }
        return sb.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        primary.resetState(postOptimization);
        if (weight != null) {
            weight.resetState(postOptimization);
        }
    }
}

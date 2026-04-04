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
 * W3C XQFT 3.0 — FTWords.
 *
 * <pre>FTWords ::= FTWordsValue FTAnyallOption?</pre>
 *
 * The terminal node in the FT expression tree: matches words or phrases.
 */
public class FTWords extends FTAbstractExpr {

    /** any, any word, all, all words, phrase */
    public enum AnyallMode {
        ANY, ANY_WORD, ALL, ALL_WORDS, PHRASE;

        public static AnyallMode fromString(final String s) {
            switch (s) {
                case "any": return ANY;
                case "any word": return ANY_WORD;
                case "all": return ALL;
                case "all words": return ALL_WORDS;
                case "phrase": return PHRASE;
                default: return ANY;
            }
        }

        @Override
        public String toString() {
            return name().toLowerCase().replace('_', ' ');
        }
    }

    private Expression wordsValue;
    private AnyallMode mode = AnyallMode.ANY;
    private FTTimes ftTimes;

    public FTWords(final XQueryContext context) {
        super(context);
    }

    public void setWordsValue(final Expression wordsValue) {
        this.wordsValue = wordsValue;
    }

    public Expression getWordsValue() {
        return wordsValue;
    }

    public void setMode(final AnyallMode mode) {
        this.mode = mode;
    }

    public AnyallMode getMode() {
        return mode;
    }

    public void setFTTimes(final FTTimes ftTimes) {
        this.ftTimes = ftTimes;
    }

    public FTTimes getFTTimes() {
        return ftTimes;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        wordsValue.analyze(contextInfo);
        if (ftTimes != null) {
            ftTimes.analyze(contextInfo);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        wordsValue.dump(dumper);
        if (mode != AnyallMode.ANY) {
            dumper.display(' ').display(mode.toString());
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(wordsValue.toString());
        if (mode != AnyallMode.ANY) {
            sb.append(' ').append(mode.toString());
        }
        return sb.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        wordsValue.resetState(postOptimization);
    }
}

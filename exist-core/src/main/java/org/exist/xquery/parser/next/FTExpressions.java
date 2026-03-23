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

import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub XQFT expression classes for the hand-written parser prototype.
 *
 * <p>Mirrors the constructors from {@code org.exist.xquery.ft.*} (post-7.0).
 * Replace with real imports when integrating with post-7.0 eXist.</p>
 */
public final class FTExpressions {

    private FTExpressions() {}

    /** SOURCE contains text FTSELECTION */
    public static class ContainsExpr extends AbstractExpression {
        private Expression source;
        private Selection ftSelection;

        public ContainsExpr(XQueryContext context) { super(context); }
        public void setSearchSource(Expression source) { this.source = source; }
        public void setFTSelection(Selection sel) { this.ftSelection = sel; }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            // Stub: evaluate source, check if it contains the FT match
            // Real implementation in org.exist.xquery.ft.FTContainsExpr
            throw new XPathException(this, "XQFT contains expression requires post-7.0 runtime");
        }

        @Override public int returnsType() { return org.exist.xquery.value.Type.BOOLEAN; }
        @Override public void dump(ExpressionDumper dumper) { dumper.display("contains text"); }
        @Override public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
            if (source != null) source.analyze(contextInfo);
        }
    }

    /** FT selection: ftOr with optional positional filters */
    public static class Selection extends AbstractExpression {
        private Expression ftOr;
        private final List<Expression> posFilters = new ArrayList<>();

        public Selection(XQueryContext context) { super(context); }
        public void setFTOr(Expression ftOr) { this.ftOr = ftOr; }
        public void addPosFilter(Expression filter) { posFilters.add(filter); }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-selection"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT word match */
    public static class Words extends AbstractExpression {
        public enum AnyallMode { ANY, ANY_WORD, ALL, ALL_WORDS, PHRASE }

        private Expression wordsValue;
        private AnyallMode mode = AnyallMode.ANY;
        private Times ftTimes;

        public Words(XQueryContext context) { super(context); }
        public void setWordsValue(Expression v) { this.wordsValue = v; }
        public void setMode(AnyallMode m) { this.mode = m; }
        public void setFTTimes(Times t) { this.ftTimes = t; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-words"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT boolean: ftand */
    public static class And extends AbstractExpression {
        private final List<Expression> operands = new ArrayList<>();
        public And(XQueryContext context) { super(context); }
        public void addOperand(Expression op) { operands.add(op); }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ftand"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT boolean: ftor */
    public static class Or extends AbstractExpression {
        private final List<Expression> operands = new ArrayList<>();
        public Or(XQueryContext context) { super(context); }
        public void addOperand(Expression op) { operands.add(op); }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ftor"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT boolean: ftnot (mild not) */
    public static class MildNot extends AbstractExpression {
        private final List<Expression> operands = new ArrayList<>();
        public MildNot(XQueryContext context) { super(context); }
        public void addOperand(Expression op) { operands.add(op); }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ftnot"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT unary not */
    public static class UnaryNot extends AbstractExpression {
        private Expression operand;
        public UnaryNot(XQueryContext context) { super(context); }
        public void setOperand(Expression op) { this.operand = op; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-not"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT primary with match options */
    public static class PrimaryWithOptions extends AbstractExpression {
        private Expression primary;
        private MatchOptions matchOptions;

        public PrimaryWithOptions(XQueryContext context) { super(context); }
        public void setPrimary(Expression p) { this.primary = p; }
        public void setMatchOptions(MatchOptions opts) { this.matchOptions = opts; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-primary-options"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT match options: stemming, language, wildcards, diacritics, case, etc. */
    public static class MatchOptions {
        public enum DiacriticsMode { INSENSITIVE, SENSITIVE }
        public enum CaseMode { INSENSITIVE, SENSITIVE, LOWERCASE, UPPERCASE }

        private boolean stemming;
        private boolean wildcards;
        private String language;
        private DiacriticsMode diacriticsMode;
        private CaseMode caseMode;

        public void setStemming(boolean v) { this.stemming = v; }
        public void setWildcards(boolean v) { this.wildcards = v; }
        public void setLanguage(String v) { this.language = v; }
        public void setDiacriticsMode(DiacriticsMode v) { this.diacriticsMode = v; }
        public void setCaseMode(CaseMode v) { this.caseMode = v; }
    }

    /** FT unit: words, sentences, or paragraphs */
    public enum Unit { WORDS, SENTENCES, PARAGRAPHS }

    /** FT range: exactly N, at least N, at most N, or from N to M */
    public static class Range extends AbstractExpression {
        public enum RangeMode { EXACTLY, AT_LEAST, AT_MOST, FROM_TO }
        private RangeMode mode = RangeMode.EXACTLY;
        private Expression expr1;
        private Expression expr2;

        public Range(XQueryContext context) { super(context); }
        public void setMode(RangeMode m) { this.mode = m; }
        public void setExpr1(Expression e) { this.expr1 = e; }
        public void setExpr2(Expression e) { this.expr2 = e; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-range"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT times: occurs N times */
    public static class Times extends AbstractExpression {
        private Range range;
        public Times(XQueryContext context) { super(context); }
        public void setRange(Range r) { this.range = r; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-times"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT window: within N words/sentences/paragraphs */
    public static class Window extends AbstractExpression {
        private Expression windowExpr;
        private Unit unit;
        public Window(XQueryContext context) { super(context); }
        public void setWindowExpr(Expression e) { this.windowExpr = e; }
        public void setUnit(Unit u) { this.unit = u; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-window"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT distance: distance range unit */
    public static class Distance extends AbstractExpression {
        private Range range;
        private Unit unit;
        public Distance(XQueryContext context) { super(context); }
        public void setRange(Range r) { this.range = r; }
        public void setUnit(Unit u) { this.unit = u; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-distance"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT content: at start / at end / entire content */
    public static class Content extends AbstractExpression {
        public enum ContentType { AT_START, AT_END, ENTIRE_CONTENT }
        private ContentType contentType;
        public Content(XQueryContext context) { super(context); }
        public void setContentType(ContentType t) { this.contentType = t; }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-content"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT order: ordered */
    public static class Order extends AbstractExpression {
        public Order(XQueryContext context) { super(context); }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-ordered"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /** FT scope: same/different sentence/paragraph */
    public static class Scope extends AbstractExpression {
        public Scope(XQueryContext context) { super(context); }

        @Override public Sequence eval(Sequence s, Item i) throws XPathException { return s; }
        @Override public int returnsType() { return org.exist.xquery.value.Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("ft-scope"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }
}

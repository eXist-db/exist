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

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.exist.dom.QName;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for the per-expression {@code optimize(CompileContext)} prototype:
 * {@link ConditionalExpression#optimize}, {@link GeneralComparison#optimize},
 * {@link LetExpr#optimize}.
 *
 * Each test parses a query into the internal expression tree, runs the
 * optimize() pass, and asserts the expected rewrite (or absence of rewrite).
 * No embedded server is required — the parser produces a fully-typed tree
 * directly from a {@link XQueryContext}.
 */
public class ExpressionOptimizeTest {

    /**
     * {@code if (1 = 1) then "yes" else "no"} — the condition folds to a
     * boolean literal, so the {@code if} should be replaced by its
     * {@code then} branch.
     */
    @Test
    public void conditionalConstantTrueFolds() throws Exception {
        final ConditionalExpression cond = (ConditionalExpression)
                parseSingle("if (1 = 1) then \"yes\" else \"no\"", ConditionalExpression.class);
        // We need the test branch to be a literal Boolean, which only happens
        // after analyze() (the comparison optimizer pass runs during optimize).
        cond.getContext().analyzeAndOptimizeIfModulesChanged(parentOf(cond));
        // The chosen branch should now be the "yes" literal.
        // Find the rewrite: the parent PathExpr's first step is the chosen branch.
        final PathExpr root = (PathExpr) parentOf(cond);
        final Expression rewritten = root.getExpression(0);
        assertNotSame(cond, rewritten, "if-then-else should have been replaced");
        assertTrue(rewritten instanceof LiteralValue, "expected a LiteralValue, got " + rewritten.getClass());
        assertEquals("yes", ((LiteralValue) rewritten).getValue().getStringValue());
        assertTrue(rewriteFired(cond.getContext(), "constant true condition"),
                "CompileContext log should record the fold");
    }

    /**
     * {@code if (1 = 2) then "yes" else "no"} — folds to the {@code else}
     * branch. Note: the parser wraps the else-branch in a
     * {@link DebuggableExpression} for debugger step-into support, so the
     * folded result is a wrapper around the literal — the optimization still
     * fired (the if-then-else is gone), the wrapper is just retained for
     * debugger fidelity.
     */
    @Test
    public void conditionalConstantFalseFolds() throws Exception {
        final ConditionalExpression cond = (ConditionalExpression)
                parseSingle("if (1 = 2) then \"yes\" else \"no\"", ConditionalExpression.class);
        cond.getContext().analyzeAndOptimizeIfModulesChanged(parentOf(cond));
        final PathExpr root = (PathExpr) parentOf(cond);
        final Expression rewritten = unwrap(root.getExpression(0));
        assertNotSame(cond, rewritten);
        assertTrue(rewritten instanceof LiteralValue,
                "expected LiteralValue (possibly via DebuggableExpression), got " + rewritten.getClass());
        assertEquals("no", ((LiteralValue) rewritten).getValue().getStringValue());
        assertTrue(rewriteFired(cond.getContext(), "constant false condition"));
    }

    /**
     * {@code if ($x) then "yes" else "no"} — non-constant condition; the
     * if-then-else must be preserved.
     */
    @Test
    public void conditionalDynamicConditionPreserved() throws Exception {
        final String query = "declare variable $x external; if ($x) then \"yes\" else \"no\"";
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse(query, ctx);
        // declare external $x
        ctx.declareVariable("x", true);
        ctx.analyzeAndOptimizeIfModulesChanged(root);
        // The last step of the PathExpr should still be a ConditionalExpression.
        final Expression last = root.getExpression(root.getLength() - 1);
        assertTrue(last instanceof ConditionalExpression,
                "expected ConditionalExpression to remain, got " + last.getClass());
    }

    /**
     * {@code 1 = 1} — both operands are literals with no dependencies, so
     * the comparison folds to a boolean literal.
     */
    @Test
    public void generalComparisonBothLiteralsFolds() throws Exception {
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse("1 = 1", ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);
        final Expression first = root.getExpression(0);
        assertTrue(first instanceof LiteralValue,
                "expected literal Boolean, got " + first.getClass());
        assertEquals("true", ((LiteralValue) first).getValue().getStringValue());
        assertTrue(rewriteFired(ctx, "constant fold"));
    }

    /**
     * {@code 1 = 2} — folds to {@code false}.
     */
    @Test
    public void generalComparisonFalseLiteralFolds() throws Exception {
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse("1 = 2", ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);
        final Expression first = root.getExpression(0);
        assertTrue(first instanceof LiteralValue);
        assertEquals("false", ((LiteralValue) first).getValue().getStringValue());
    }

    /**
     * {@code $x = 1} — left operand is a variable reference; the comparison
     * must NOT be folded.
     */
    @Test
    public void generalComparisonWithVarPreserved() throws Exception {
        final String query = "declare variable $x external; $x = 1";
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse(query, ctx);
        ctx.declareVariable("x", 1);
        ctx.analyzeAndOptimizeIfModulesChanged(root);
        // Find the comparison — it is not at index 0 because the prolog
        // declarations come first; just check none of the steps is the folded literal.
        boolean foundComparison = false;
        for (int i = 0; i < root.getLength(); i++) {
            if (root.getExpression(i) instanceof GeneralComparison) {
                foundComparison = true;
                break;
            }
        }
        assertTrue(foundComparison, "GeneralComparison with variable left side should be preserved");
    }

    /**
     * {@code let $x := 1 return 42} — variable is bound but never referenced;
     * the let should be dropped. The parser wraps the return body in a
     * {@link DebuggableExpression} for debugger step-into support, so the
     * dropped-let result is the wrapper retained around the literal.
     */
    @Test
    public void unusedLetIsDropped() throws Exception {
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse("let $x := 1 return 42", ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);
        // Root's first step should now be the return body literal "42"
        // (possibly via DebuggableExpression), not a LetExpr.
        final Expression first = unwrap(root.getExpression(0));
        assertTrue(first instanceof LiteralValue,
                "expected return body literal, got " + first.getClass());
        assertEquals("42", ((LiteralValue) first).getValue().getStringValue());
        assertTrue(rewriteFired(ctx, "unused let-binding"));
    }

    /**
     * {@code let $x := 1 return $x + 1} — variable IS referenced in the
     * return; the let must be preserved.
     */
    @Test
    public void usedLetIsPreserved() throws Exception {
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse("let $x := 1 return $x + 1", ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);
        final Expression first = root.getExpression(0);
        assertTrue(first instanceof LetExpr,
                "expected LetExpr to remain, got " + first.getClass());
    }

    // -- FLWOR loop-invariant hoisting -----------------------------------

    /**
     * Classic XMark pattern: the inner {@code for}'s input is loop-invariant
     * w.r.t. the outer {@code $p}, so it should be lifted into a synthesised
     * let inserted before the outer {@code for}.
     */
    @Test
    public void innerForInvariantInputIsHoistedAboveOuterFor() throws Exception {
        final String query = """
                for $p in (1, 2, 3)
                let $items := for $i in (10, 20) return $i * $p
                return $items""";
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse(query, ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);

        final Expression first = root.getExpression(0);
        assertTrue(first instanceof LetExpr,
                "expected synthesised hoist let at root, got " + first.getClass());
        final LetExpr hoist = (LetExpr) first;
        assertTrue(hoist.getVariable().getLocalPart().startsWith("__hoisted_"),
                "expected __hoisted_* local part, got " + hoist.getVariable());
        assertTrue(hoist.getReturnExpression() instanceof ForExpr,
                "expected outer for as the new let's body, got "
                        + hoist.getReturnExpression().getClass());
        assertTrue(rewriteFired(ctx, "HOIST"),
                "CompileContext log should record the hoist");
    }

    /**
     * Inner {@code for}'s input references the outer {@code $p}, so the
     * hoist must NOT fire — moving the input outside the loop would break
     * semantics.
     */
    @Test
    public void innerForReferencingOuterVarIsNotHoisted() throws Exception {
        final String query = """
                for $p in (1, 2, 3)
                let $items := for $i in ($p, $p + 1) return $i
                return $items""";
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse(query, ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);

        final Expression first = root.getExpression(0);
        assertTrue(first instanceof ForExpr,
                "expected outer ForExpr to remain unwrapped, got " + first.getClass());
    }

    /**
     * No outer FLWOR scope to hoist over — a top-level {@code for} whose
     * input is just a literal sequence should NOT be wrapped (no benefit,
     * and {@link CompileContext#flworChainDepth()} is 1, gating us out).
     */
    @Test
    public void topLevelForIsNotWrapped() throws Exception {
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse("for $i in (1, 2, 3) return $i", ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);
        final Expression first = root.getExpression(0);
        assertTrue(first instanceof ForExpr,
                "no hoisting for a top-level for, got " + first.getClass());
    }

    /**
     * Let-prefix scenario: outer chain is {@code let $a := … for $p := …} and
     * the inner for's input references {@code $a} (let-prefix, once-bound).
     * The hoist must fire and be SPLICED between {@code $a}'s let and
     * {@code $p}'s for, not at the chain head — otherwise the hoisted
     * expression would reference {@code $a} before its binding.
     */
    @Test
    public void letPrefixReferenceIsHoistedMidChain() throws Exception {
        final String query = """
                let $a := (100, 200, 300)
                for $p in (1, 2, 3)
                let $items := for $i in ($a, $a) return $i + $p
                return $items""";
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse(query, ctx);
        ctx.analyzeAndOptimizeIfModulesChanged(root);

        // The chain head is still LetExpr($a) — splice happens AFTER it.
        final Expression first = root.getExpression(0);
        assertTrue(first instanceof LetExpr,
                "chain head should still be LetExpr($a), got " + first.getClass());
        final LetExpr aLet = (LetExpr) first;
        assertEquals("a", aLet.getVariable().getLocalPart());

        // aLet.returnExpr should now be the synthesised hoist let.
        final Expression spliced = aLet.getReturnExpression();
        assertTrue(spliced instanceof LetExpr,
                "expected hoist let spliced after $a, got " + spliced.getClass());
        final LetExpr hoist = (LetExpr) spliced;
        assertTrue(hoist.getVariable().getLocalPart().startsWith("__hoisted_"),
                "expected __hoisted_* var, got " + hoist.getVariable());
        assertTrue(hoist.getReturnExpression() instanceof ForExpr,
                "expected ForExpr after the hoist let, got "
                        + hoist.getReturnExpression().getClass());
        assertTrue(rewriteFired(ctx, "HOIST"),
                "CompileContext log should record the hoist");
    }

    // -- Helpers --------------------------------------------------------

    private static PathExpr parse(final String query, final XQueryContext context)
            throws RecognitionException, XPathException, TokenStreamException,
                   QName.IllegalQNameException {
        final XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
        final XQueryParser xparser = new XQueryParser(lexer);
        xparser.xpath();
        if (xparser.foundErrors()) {
            fail(xparser.getErrorMessage());
        }
        final XQueryAST ast = (XQueryAST) xparser.getAST();
        final XQueryTreeParser treeParser = new XQueryTreeParser(context);
        final PathExpr expr = new PathExpr(context);
        treeParser.xpath(ast, expr);
        if (treeParser.foundErrors()) {
            fail(treeParser.getErrorMessage());
        }
        return expr;
    }

    /**
     * Parses {@code query} and returns the first sub-expression of the
     * specified class, asserting it is present.
     */
    private static Expression parseSingle(final String query, final Class<?> klass)
            throws Exception {
        final XQueryContext ctx = new XQueryContext();
        final PathExpr root = parse(query, ctx);
        for (int i = 0; i < root.getLength(); i++) {
            if (klass.isInstance(root.getExpression(i))) {
                final Expression target = root.getExpression(i);
                // Stash the parent on a side-channel so tests can navigate.
                PARENT.set(root);
                return target;
            }
        }
        fail("did not find a " + klass.getSimpleName() + " in: " + query);
        return null;
    }

    private static final ThreadLocal<Expression> PARENT = new ThreadLocal<>();

    private static Expression parentOf(final Expression e) {
        return PARENT.get();
    }

    private static boolean rewriteFired(final XQueryContext ctx, final String reasonContains) {
        final CompileContext cc = ctx.getLastCompileContext();
        if (cc == null) {
            return false;
        }
        return cc.log().stream().anyMatch(s -> s.contains(reasonContains));
    }

    /**
     * Unwraps a {@link DebuggableExpression} or single-step {@link PathExpr}
     * to expose the underlying expression. The parser inserts these wrappers
     * for debugger support; unwrapping is needed when verifying optimization
     * results structurally.
     */
    private static Expression unwrap(Expression e) {
        while (true) {
            if (e instanceof DebuggableExpression d) {
                // DebuggableExpression.getFirst() exposes the wrapped expression
                // (LiteralValue, etc.) without going through getSubExpression,
                // which AbstractExpression's default implementation would throw on.
                e = d.getFirst();
            } else if (e instanceof PathExpr p && p.getLength() == 1) {
                e = p.getExpression(0);
            } else {
                return e;
            }
        }
    }
}

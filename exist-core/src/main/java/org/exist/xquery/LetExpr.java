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

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements an XQuery let-expression.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class LetExpr extends BindingExpression {

    private boolean scoreBinding = false;

    public LetExpr(XQueryContext context) {
        super(context);
    }

    /**
     * XQFT 3.0 §2.3: Mark this let binding as a score variable binding.
     * When true, the variable is bound to the score (xs:double in [0,1])
     * of the input expression rather than the expression's value.
     */
    public void setScoreBinding(final boolean scoreBinding) {
        this.scoreBinding = scoreBinding;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.LET;
    }

    @Override
    public Expression optimize(final CompileContext cc) throws XPathException {
        // Chain-head lets push a fresh FLWOR scope (continuing clauses share it).
        // The scope tracks visible variables and accumulates hoist actions
        // queued by inner FLWOR optimize() passes.
        final boolean enteredScope = getPreviousClause() == null;
        if (enteredScope) {
            cc.enterFlworChain();
        }

        // Recurse the input first (the let-variable is NOT yet in scope for
        // its own initializer, per XQuery semantics). This also gives any
        // inner FLWORs in the input a chance to register hoists targeting
        // outer scopes.
        if (inputSequence != null) {
            inputSequence = inputSequence.optimize(cc);
        }

        // Now make this let's variable visible to the rest of the chain.
        // Score bindings (XQFT 3.0 §2.3) bind a synthesized double rather than
        // the input value — exclude from FLWOR-scope tracking to keep the
        // hoist invariance check honest.
        if (varName != null && !scoreBinding) {
            cc.addVisibleFlworVar(varName);
        }

        if (returnExpr != null) {
            returnExpr = returnExpr.optimize(cc);
        }

        // Drop the let if its body is a literal — the variable is by definition
        // unreferenced, and the input sequence is side-effect-free. Guards:
        //   - score binding stays (XQFT 3.0 §2.3),
        //   - no previous clause (avoid having to repair clause-chain
        //     previousClause pointers),
        //   - inputSequence is a LiteralValue (no side effects to preserve),
        //   - the unwrapped returnExpr is a LiteralValue (so it cannot
        //     reference any variable).
        //
        // This is intentionally narrow for v1: it captures `let $x := 1 return
        // 42` but not `let $x := 1 return $y + 1`. We avoid a structural
        // variable-reference scan because BasicExpressionVisitor does not
        // traverse through FilteredExpression / GeneralComparison / OpNumeric,
        // and the eXist Dependency flags are not reliable after the analyze()
        // pass has popped the let's variable from scope. A precise unused-var
        // check belongs in a follow-up.
        Expression result = this;
        if (!scoreBinding
                && varName != null
                && !(returnExpr instanceof FLWORClause)
                && getPreviousClause() == null
                && (inputSequence instanceof LiteralValue)
                && (unwrap(returnExpr) instanceof LiteralValue)) {
            result = cc.replaceWith(this, returnExpr, "unused let-binding $" + varName);
        }

        if (enteredScope) {
            result = cc.applyHoistsAndExitChain(result);
        }
        return result;
    }

    /**
     * Strips {@link DebuggableExpression} and single-step {@link PathExpr}
     * wrappers to expose the underlying expression. Used to recognise a
     * trivially literal return body even when the parser has wrapped it for
     * debugger support.
     */
    private static Expression unwrap(Expression e) {
        while (true) {
            if (e instanceof DebuggableExpression d) {
                e = d.getFirst();
            } else if (e instanceof PathExpr p && p.getLength() == 1) {
                e = p.getExpression(0);
            } else {
                return e;
            }
        }
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        //Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            varContextInfo.addFlag(NON_UPDATING_CONTEXT);
            inputSequence.analyze(varContextInfo);
            //Declare the iteration variable
            final LocalVariable inVar = new LocalVariable(varName);
            inVar.setSequenceType(sequenceType);
            inVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(inVar);
            //Reset the context position
            context.setContextSequencePosition(0, null);

            returnExpr.analyze(contextInfo);
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()){
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        context.expressionStart(this);
        context.pushDocumentContext();
        try {
            //Save the local variable stack
            LocalVariable mark = context.markLocalVariables(false);
            Sequence in;
            LocalVariable var;
            Sequence resultSequence = null;
            try {
                // evaluate input sequence
                in = inputSequence.eval(contextSequence, null);
                clearContext(getExpressionId(), in);
                // Declare the iteration variable
                var = createVariable(varName);
                var.setSequenceType(sequenceType);
                context.declareVariableBinding(var);
                if (scoreBinding) {
                    // XQFT 3.0 §2.3: score binding — bind variable to the score
                    // of the expression. Naive implementation: 1.0 if non-empty, 0.0 if empty.
                    var.setValue(new DoubleValue(this, in.isEmpty() ? 0.0 : 1.0));
                } else {
                    var.setValue(in);
                }
                if (sequenceType == null)
                    {var.checkType();} //Just because it makes conversions !
                var.setContextDocs(inputSequence.getContextDocSet());
                registerUpdateListener(in);

                try {
                    resultSequence = returnExpr.eval(contextSequence, null);
                } catch (final WhileClause.WhileTerminationException e) {
                    resultSequence = Sequence.EMPTY_SEQUENCE;
                }
                if (getPreviousClause() == null && WhileClause.isTerminated()) {
                    WhileClause.clearTerminated();
                }

                if (sequenceType != null) {
                    Cardinality actualCardinality;
                    if (var.getValue().isEmpty()) {actualCardinality = Cardinality.EMPTY_SEQUENCE;}
                    else if (var.getValue().hasMany()) {actualCardinality = Cardinality._MANY;}
                    else {actualCardinality = Cardinality.EXACTLY_ONE;}
                    //Type.EMPTY is *not* a subtype of other types ; checking cardinality first
                    if (!sequenceType.getCardinality().isSuperCardinalityOrEqualOf(actualCardinality))
                        {throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Invalid cardinality for variable $" + varName +
                            ". Expected " +
                            sequenceType.getCardinality().getHumanDescription() +
                            ", got " + actualCardinality.getHumanDescription(), in);}
                    //TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
                    if (!Type.subTypeOf(sequenceType.getPrimaryType(), Type.NODE)) {
                        if (!var.getValue().isEmpty() && !Type.subTypeOf(var.getValue()
                                .getItemType(), sequenceType.getPrimaryType())) {
                            throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Invalid type for variable $" + varName +
                                ". Expected " + Type.getTypeName(sequenceType.getPrimaryType()) +
                                ", got " +Type.getTypeName(var.getValue().getItemType()), in);
                        }
                    //Here is an attempt to process the nodes correctly
                    } else {
                        //Same as above : we probably may factorize 
                        if (!var.getValue().isEmpty() && !sequenceType.checkType(var.getValue())) {
                            final Sequence value = var.getValue();
                            final SequenceType valueType = new SequenceType(value.getItemType(), value.getCardinality());
                            if ((!value.isEmpty()) && sequenceType.getPrimaryType() == Type.DOCUMENT && value.getItemType() == Type.DOCUMENT) {
                                // it's a document... we need to get the document element's name
                                final NodeValue nvItem = (NodeValue) value.itemAt(0);
                                final Document doc;
                                if (nvItem instanceof Document) {
                                    doc = (Document) nvItem;
                                } else {
                                    doc = nvItem.getOwnerDocument();
                                }
                                if (doc != null) {
                                    final Element elem = doc.getDocumentElement();
                                    if (elem != null) {
                                        valueType.setNodeName(new QName(elem.getLocalName(), elem.getNamespaceURI()));
                                    }
                                }
                            }

                            if ((!value.isEmpty()) && sequenceType.getPrimaryType() == Type.ELEMENT && value.getItemType() == Type.ELEMENT) {
                                final NodeValue nvItem = (NodeValue) value.itemAt(0);
                                valueType.setNodeName(nvItem.getQName());
                            }

                            throw new XPathException(
                                    this,
                                    ErrorCodes.XPTY0004,
                                    String.format("Invalid type for variable $%s. Expected %s, got %s", varName, sequenceType.toString(), valueType), in);
                        }
                    }
                }
            } finally {
                // Restore the local variable stack
                context.popLocalVariables(mark, resultSequence);
            }
            clearContext(getExpressionId(), in);
            if (context.getProfiler().isEnabled())
                {context.getProfiler().end(this, "", resultSequence);}
            if (resultSequence == null)
                {return Sequence.EMPTY_SEQUENCE;}
            if (!(resultSequence instanceof DeferredFunctionCall)) {
                setActualReturnType(resultSequence.getItemType());
            }
            if (getPreviousClause() == null) {
                resultSequence = postEval(resultSequence);
            }
            return resultSequence;
        } finally {
            context.popDocumentContext();
            context.expressionEnd(this);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("let ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        dumper.display(" := ");
        inputSequence.dump(dumper);
        dumper.endIndent();
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {dumper.display(", ");}
        else
            {dumper.nl().display("return ");}
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent();
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("let ");
        result.append("$").append(varName);
        result.append(" := ");
        result.append(inputSequence.toString());
        result.append(" ");
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {result.append(", ");}
        else
            {result.append("return ");}
        result.append(returnExpr.toString());
        return result.toString();
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitLetExpression(this);
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> variables = new HashSet<>();

        final QName variable = getVariable();
        variables.add(variable);

        final LocalVariable startVar = getStartVariable();
        if (startVar != null) {
            variables.add(startVar.getQName());
        }

        return variables;
    }
}
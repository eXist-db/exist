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

    public LetExpr(XQueryContext context) {
        super(context);
    }

    @Override
    public ClauseType getType() {
        return ClauseType.LET;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        //Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
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
                var.setValue(in);
                if (sequenceType == null)
                    {var.checkType();} //Just because it makes conversions !
                var.setContextDocs(inputSequence.getContextDocSet());
                registerUpdateListener(in);

                // XQuery 4.0 PR1501: coerce map(K, V) / array(T) bindings to
                // the declared component types BEFORE the body runs, so
                // subsequent uses of the variable (including instance-of
                // tests) see the coerced shape. The post-eval cardinality /
                // item-type check below still runs and will raise XPTY0004 if
                // coercion fails or the value can't be made to fit.
                if (sequenceType != null && !var.getValue().isEmpty()
                        && sequenceType.getFunctionParamTypes() != null
                        && (sequenceType.getPrimaryType() == Type.MAP_ITEM
                                || sequenceType.getPrimaryType() == Type.ARRAY_ITEM)) {
                    final Sequence coerced = MapTypeCoercion.tryCoerce(
                            context, sequenceType, var.getValue());
                    if (coerced != null) {
                        var.setValue(coerced);
                    }
                }

                // XQuery 4.0 PR1131 (coercion-in-variables): apply function conversion
                // to atomic typed bindings BEFORE the body runs. This casts xs:untypedAtomic,
                // promotes numerics (xs:integer/xs:decimal -> xs:float/xs:double, xs:float -> xs:double),
                // converts xs:anyURI -> xs:string, and applies subtype casting permitted by 4.0.
                if (sequenceType != null && context.getXQueryVersion() >= 40
                        && !var.getValue().isEmpty()
                        && Type.subTypeOf(sequenceType.getPrimaryType(), Type.ANY_ATOMIC_TYPE)
                        && !Type.subTypeOf(var.getValue().getItemType(), sequenceType.getPrimaryType())) {
                    final Sequence coerced = coerceAtomicSequence(var.getValue(), sequenceType.getPrimaryType());
                    if (coerced != null) {
                        var.setValue(coerced);
                    }
                }

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
                        // For typed map(K, V) and array(T) bindings, walk the
                        // structure so a shape mismatch raises XPTY0004 instead
                        // of silently accepting the value. In XQuery 4.0 mode,
                        // first attempt PR1501 coercion: if every entry's key
                        // and value can be converted to the declared key/value
                        // types, build a coerced map and bind that instead.
                        if (!var.getValue().isEmpty() && sequenceType.getFunctionParamTypes() != null
                                && (sequenceType.getPrimaryType() == Type.MAP_ITEM
                                        || sequenceType.getPrimaryType() == Type.ARRAY_ITEM)) {
                            final Sequence coerced = MapTypeCoercion.tryCoerce(
                                    context, sequenceType, var.getValue());
                            if (coerced != null) {
                                var.setValue(coerced);
                            } else {
                                for (final SequenceIterator i = var.getValue().iterate(); i.hasNext(); ) {
                                    final Item item = i.nextItem();
                                    if (!sequenceType.checkType(item)) {
                                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                            "Invalid value for variable $" + varName +
                                            ". Expected " + sequenceType + ", got value not matching the structural type");
                                    }
                                }
                            }
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

    /**
     * XQ4 PR1131 atomic coercion: cast each item to the declared atomic type
     * (function-conversion semantics). Returns null if any item cannot be cast,
     * leaving the original value in place so the existing XPTY0004 path runs.
     */
    private Sequence coerceAtomicSequence(final Sequence value, final int targetType) {
        try {
            final ValueSequence out = new ValueSequence(value.getItemCount());
            for (final SequenceIterator it = value.iterate(); it.hasNext(); ) {
                final Item item = it.nextItem();
                final AtomicValue atomized = item.atomize();
                if (Type.subTypeOf(atomized.getType(), targetType)) {
                    out.add(atomized);
                } else {
                    out.add(atomized.convertTo(targetType));
                }
            }
            return out;
        } catch (final XPathException e) {
            return null;
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
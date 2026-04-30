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

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.RecordType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.functions.map.AbstractMapType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author wolf
 */
public class UserDefinedFunction extends Function implements Cloneable {

    private final List<QName> parameters = new ArrayList<>(5);
    private final Set<QName> parameterNames = new HashSet<>();
    protected boolean visited = false;
    private Expression body;
    private Sequence[] currentArguments = null;
    private DocumentSet[] contextDocs = null;
    private boolean bodyAnalyzed = false;
    private FunctionCall call;
    private boolean hasBeenReset = false;
    private List<ClosureVariable> closureVariables = null;
    private boolean passContextToBody = false;

    public UserDefinedFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Expression getFunctionBody() {
        return body;
    }

    public void setFunctionBody(Expression body) {
        this.body = body.simplify();
    }

    /**
     * Mark this UDF as a wrapper for an internal function (created by
     * {@link FunctionFactory#wrap}). Wrapper functions pass the evaluation
     * context through to their body so that context-dependent built-in
     * functions (like fn:id, fn:idref, fn:string, etc.) can access the
     * focus when called via function references.
     */
    public void setPassContextToBody(boolean passContext) {
        this.passContextToBody = passContext;
    }

    public void addVariable(final String varName) throws XPathException {
        try {
            final QName qname = QName.parse(context, varName, null);
            addVariable(qname);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(this, ErrorCodes.XPST0081, "No namespace defined for prefix " + varName);
        }
    }

    public void addVariable(QName varName) throws XPathException {
        if (!parameterNames.add(varName)) {
            throw new XPathException(this, ErrorCodes.XQST0039, "function " + getName() + " already has a parameter with the name " + varName);
        }

        parameters.add(varName);
    }

    public void setArguments(Sequence[] args, DocumentSet[] contextDocs) throws XPathException {
        this.currentArguments = args;
        this.contextDocs = contextDocs;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        hasBeenReset = false;

        if (call != null && !call.isRecursive()) {
            // Save the local variable stack
            final LocalVariable mark = context.markLocalVariables(true);
            if (closureVariables != null) {
                // if this is a inline function, context variables are known
                context.restoreStack(closureVariables);
            }
            try {
                LocalVariable var;
                for (final QName varName : parameters) {
                    var = new LocalVariable(varName);
                    context.declareVariableBinding(var);
                }

                final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
                newContextInfo.setParent(this);
                if (!bodyAnalyzed) {
                    if (body != null) {
                        if (!getSignature().isUpdating()) {
                            // Non-updating function body: updating expressions not allowed
                            newContextInfo.addFlag(NON_UPDATING_CONTEXT);
                        } else {
                            // Updating function body: updating expressions are allowed
                            newContextInfo.removeFlag(NON_UPDATING_CONTEXT);
                        }
                        body.analyze(newContextInfo);

                        // XUST0002: updating function body must be updating (or vacuous)
                        if (getSignature().isUpdating() && !body.isUpdating()
                                && !body.isVacuous()) {
                            throw new XPathException(this, ErrorCodes.XUST0002,
                                    "body of updating function " + getName() +
                                    " must be an updating expression or an empty sequence");
                        }
                    }
                    bodyAnalyzed = true;
                }
            } finally {
                // restore the local variable stack
                context.popLocalVariables(mark);
            }
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
//        context.expressionStart(this);
        context.stackEnter(this);
        // make sure reset state is called after query has finished
        hasBeenReset = false;
        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(true);
        if (closureVariables != null) {
            context.restoreStack(closureVariables);
        }
        Sequence result = null;
        try {
            final SequenceType[] argTypes = getSignature().getArgumentTypes();

            // Evaluate all argument values first, BEFORE declaring any parameters.
            // Default value expressions must be evaluated in the prolog's variable scope,
            // not the function body scope (XQ4 spec: default sees variables in scope at
            // the function declaration, not other parameters). Context is passed so that
            // default values like "." can access the context item at the call site.
            final Sequence[] argValues = new Sequence[parameters.size()];
            for (int i = 0; i < parameters.size(); i++) {
                if (i < currentArguments.length) {
                    argValues[i] = currentArguments[i];
                } else if (argTypes[i] instanceof FunctionParameterSequenceType &&
                           ((FunctionParameterSequenceType) argTypes[i]).hasDefaultValue()) {
                    argValues[i] = ((FunctionParameterSequenceType) argTypes[i])
                            .getDefaultValue().eval(contextSequence, contextItem);
                } else {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Missing required argument $" + parameters.get(i));
                }
            }

            // Now declare all parameters with their resolved values
            for (int i = 0; i < parameters.size(); i++) {
                final QName varName = parameters.get(i);
                final LocalVariable var = new LocalVariable(varName);

                var.setValue(argValues[i]);
                if (contextDocs != null && i < contextDocs.length) {
                    var.setContextDocs(contextDocs[i]);
                }
                context.declareVariableBinding(var);

                Cardinality actualCardinality;
                if (argValues[i].isEmpty()) {
                    actualCardinality = Cardinality.EMPTY_SEQUENCE;
                } else if (argValues[i].hasMany()) {
                    actualCardinality = Cardinality._MANY;
                } else {
                    actualCardinality = Cardinality.EXACTLY_ONE;
                }

                if (!argTypes[i].getCardinality().isSuperCardinalityOrEqualOf(actualCardinality)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004, "Invalid cardinality for parameter $" + varName +
                            ". Expected " + argTypes[i].getCardinality().getHumanDescription() +
                            ", got " + argValues[i].getItemCount());
                }

                // XQuery 4.0: record type validation at runtime, with function-coercion
                // applied per-field (untypedAtomic to declared type, numeric promotion,
                // anyURI to xs:string promotion, XQ4 implicit casts and relabeling).
                final SequenceType argType = argTypes[i];
                if (argType.isRecordType() && argType.getRecordType() != null && !argValues[i].isEmpty()) {
                    final RecordType rt = argType.getRecordType();
                    final ValueSequence coerced = new ValueSequence();
                    for (final SequenceIterator iter = argValues[i].iterate(); iter.hasNext(); ) {
                        final Item item = iter.nextItem();
                        if (!Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
                            throw new XPathException(this, ErrorCodes.XPTY0004,
                                    "Argument $" + varName + " expected " + rt +
                                            " but got " + Type.getTypeName(item.getType()));
                        }
                        final AbstractMapType inMap = (AbstractMapType) item;
                        final AbstractMapType outMap = coerceRecordFields(inMap, rt, varName);
                        if (!rt.matches(outMap)) {
                            throw new XPathException(this, ErrorCodes.XPTY0004,
                                    "Argument $" + varName + " does not match " + rt);
                        }
                        coerced.add(outMap);
                    }
                    argValues[i] = coerced;
                    var.setValue(coerced);
                }
            }
            // For wrapper functions (created by FunctionFactory.wrap for internal
            // function references), pass the context through so context-dependent
            // built-in functions can access the focus. For regular user-declared
            // functions, the focus is absent per the XQuery spec.
            if (passContextToBody) {
                result = body.eval(contextSequence, contextItem);
            } else {
                result = body.eval(null, null);
            }
            return result;
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark, result);
            context.stackLeave(this);
//            context.expressionEnd(this);
        }
    }

    /**
     * Apply function-coercion to the declared fields of a record-typed argument.
     * For each declared field that is present in the input map, the value sequence
     * is atomized (so XML nodes and arrays flow through correctly) and each item
     * is coerced against the declared field type (untypedAtomic to typed, numeric
     * promotion, anyURI to xs:string, XQ4 implicit casts and relabeling, with
     * union/choice alternatives tried in order). Nested record types recurse.
     * Fields not declared in the record type (in extensible records) are passed
     * through unchanged.
     */
    private AbstractMapType coerceRecordFields(final AbstractMapType inMap, final RecordType rt,
            final QName varName) throws XPathException {
        AbstractMapType out = inMap;
        for (final RecordType.FieldDeclaration field : rt.getFieldDeclarations()) {
            final SequenceType fieldType = field.getType();
            if (fieldType == null) {
                continue;
            }
            final AtomicValue key = new StringValue(field.getName());
            if (!inMap.contains(key)) {
                continue;
            }
            final Sequence value = inMap.get(key);
            final Sequence coerced = coerceRecordFieldValue(value, fieldType);
            if (coerced != value) {
                out = out.put(key, coerced);
            }
        }
        return out;
    }

    private Sequence coerceRecordFieldValue(final Sequence value, final SequenceType fieldType)
            throws XPathException {
        final int requiredType = fieldType.getPrimaryType();
        // Nested record type: recurse into each map item.
        if (fieldType.isRecordType() && fieldType.getRecordType() != null) {
            final ValueSequence result = new ValueSequence();
            boolean changed = false;
            for (final SequenceIterator vi = value.iterate(); vi.hasNext(); ) {
                final Item item = vi.nextItem();
                if (item instanceof AbstractMapType inner) {
                    final AbstractMapType coercedInner =
                            coerceRecordFields(inner, fieldType.getRecordType(), null);
                    if (coercedInner != inner) {
                        changed = true;
                    }
                    result.add(coercedInner);
                } else {
                    result.add(item);
                }
            }
            return changed ? result : value;
        }
        // For atomic and choice (union) field types, atomize first so that node
        // and array values flow through (e.g. <a>0</a> -> xs:untypedAtomic('0'),
        // [1,2,3] -> 1,2,3). Then per-item apply function-coercion.
        final boolean isAtomic = Type.subTypeOf(requiredType, Type.ANY_ATOMIC_TYPE);
        final boolean isChoice = fieldType.isChoiceType();
        if (!isAtomic && !isChoice) {
            return value;
        }
        final Sequence atomized = Atomize.atomize(value);
        final ValueSequence result = new ValueSequence();
        boolean changed = atomized != value;
        for (final SequenceIterator vi = atomized.iterate(); vi.hasNext(); ) {
            final Item original = vi.nextItem();
            Item coerced;
            if (isChoice) {
                coerced = coerceItemForChoice(original, fieldType.getChoiceAlternatives());
            } else {
                try {
                    coerced = DynamicTypeCheck.coerceAtomicItem(context, this, original, requiredType);
                } catch (final XPathException coercionError) {
                    coerced = original;
                }
            }
            if (coerced != original) {
                changed = true;
            }
            result.add(coerced);
        }
        return changed ? result : (atomized != value ? atomized : value);
    }

    private Item coerceItemForChoice(final Item item, final java.util.List<SequenceType> alternatives) {
        // First pass: if the item already matches any alternative as-is, keep it.
        for (final SequenceType alt : alternatives) {
            if (alt.checkType(item)) {
                return item;
            }
        }
        // Second pass: try to coerce/relabel against each alternative; first
        // successful coercion wins. Mirrors the XQ4 PR1132 union coercion order.
        for (final SequenceType alt : alternatives) {
            final int altType = alt.getPrimaryType();
            if (!Type.subTypeOf(altType, Type.ANY_ATOMIC_TYPE)) {
                continue;
            }
            try {
                final Item coerced = DynamicTypeCheck.coerceAtomicItem(context, this, item, altType);
                if (coerced != item) {
                    return coerced;
                }
            } catch (final XPathException ignored) {
                // try next alternative
            }
        }
        return item;
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        final FunctionSignature signature = getSignature();
        if (signature.getName() != null) {
            dumper.display(signature.getName());
        }
        dumper.display('(');
        for (int i = 0; i < signature.getArgumentTypes().length; i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            dumper.display('$');
            dumper.display(getParameters().get(i));
            dumper.display(" as ");
            dumper.display(signature.getArgumentTypes()[i]);
        }
        dumper.display(") as ");
        dumper.display(signature.getReturnType().toString());
    }

    @Override
    public String toString() {
        final FunctionSignature signature = getSignature();
        final StringBuilder buf = new StringBuilder();
        if (signature.getName() != null) {
            buf.append(signature.getName());
        }
        buf.append('(');
        for (int i = 0; i < signature.getArgumentTypes().length; i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append('$');
            buf.append(getParameters().get(i));
            buf.append(" as ");
            buf.append(signature.getArgumentTypes()[i]);
        }
        buf.append(") as ");
        buf.append(signature.getReturnType());
        return buf.toString();
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM
                + Dependency.CONTEXT_POSITION;
    }

    @Override
    public void resetState(boolean postOptimization) {
        if (hasBeenReset) {
            return;
        }
        hasBeenReset = true;

        super.resetState(postOptimization);
        // Question: understand this test. Why not reset even is not in recursion ?
        // Answer: would lead to an infinite loop if the function is recursive.
        bodyAnalyzed = false;
        if (body != null) {
            body.resetState(postOptimization);
        }

        if (!postOptimization) {
            currentArguments = null;
            contextDocs = null;
        }
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        if (visited) {
            return;
        }
        visited = true;
        visitor.visitUserFunction(this);
    }

    /**
     * Return the functions parameters list
     *
     * @return List of function parameters
     */
    public List<QName> getParameters() {
        return parameters;
    }

    @Override
    public synchronized Object clone() {
        try {
            final UserDefinedFunction clone = (UserDefinedFunction) super.clone();

            clone.currentArguments = null;
            clone.contextDocs = null;

            clone.body = this.body; // so body will be analyzed and optimized for all calls of such functions in recursion.

            return clone;
        } catch (final CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    public FunctionCall getCaller() {
        return call;
    }

    public void setCaller(FunctionCall call) {
        this.call = call;
    }

    public List<ClosureVariable> getClosureVariables() {
        return closureVariables;
    }

    public void setClosureVariables(List<ClosureVariable> vars) {
        this.closureVariables = vars;
        if (vars != null) {
            // register the closure with the context so it gets cleared after execution
            context.pushClosure(this);
        }
    }

    protected Sequence[] getCurrentArguments() {
        return currentArguments;
    }
}

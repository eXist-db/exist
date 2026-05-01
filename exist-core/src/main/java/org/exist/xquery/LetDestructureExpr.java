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
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements XQuery 4.0 let destructuring:
 * <ul>
 *   <li>{@code let $($x, $y) := (1, 2)} — sequence destructuring</li>
 *   <li>{@code let $[$x, $y] := [1, 2]} — array destructuring</li>
 *   <li>{@code let ${$x, $y} := map{'x':1,'y':2}} — map destructuring</li>
 * </ul>
 */
public class LetDestructureExpr extends AbstractFLWORClause {

    public enum DestructureMode {
        SEQUENCE, ARRAY, MAP
    }

    private final DestructureMode mode;
    private final List<QName> varNames;
    private final List<SequenceType> varTypes;
    private Expression inputSequence;

    public LetDestructureExpr(final XQueryContext context, final DestructureMode mode) {
        super(context);
        this.mode = mode;
        this.varNames = new ArrayList<>();
        this.varTypes = new ArrayList<>();
    }

    public void addVariable(final QName name, final SequenceType type) {
        varNames.add(name);
        varTypes.add(type);
    }

    public void setInputSequence(final Expression seq) {
        this.inputSequence = seq.simplify();
    }

    public void setOverallType(final SequenceType type) {
        // Reserved for future type checking of overall destructure type
    }

    @Override
    public ClauseType getType() {
        return switch (mode) {
            case SEQUENCE -> ClauseType.LET_SEQ_DESTRUCTURE;
            case ARRAY -> ClauseType.LET_ARRAY_DESTRUCTURE;
            case MAP -> ClauseType.LET_MAP_DESTRUCTURE;
        };
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            inputSequence.analyze(varContextInfo);

            for (int i = 0; i < varNames.size(); i++) {
                final LocalVariable var = new LocalVariable(varNames.get(i));
                if (varTypes.get(i) != null) {
                    var.setSequenceType(varTypes.get(i));
                }
                context.declareVariableBinding(var);
            }

            context.setContextSequencePosition(0, null);
            returnExpr.analyze(contextInfo);
        } finally {
            context.popLocalVariables(mark);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        context.expressionStart(this);
        context.pushDocumentContext();
        try {
            final LocalVariable mark = context.markLocalVariables(false);
            Sequence resultSequence = null;
            try {
                final Sequence input = inputSequence.eval(contextSequence, null);

                switch (mode) {
                    case SEQUENCE -> bindSequenceVars(input);
                    case ARRAY -> bindArrayVars(input);
                    case MAP -> bindMapVars(input);
                }

                resultSequence = returnExpr.eval(contextSequence, null);
            } finally {
                context.popLocalVariables(mark, resultSequence);
            }
            if (resultSequence == null) {
                return Sequence.EMPTY_SEQUENCE;
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

    private void bindSequenceVars(final Sequence input) throws XPathException {
        for (int i = 0; i < varNames.size(); i++) {
            final LocalVariable var = createVariable(varNames.get(i));
            final SequenceType type = varTypes.get(i);
            if (type != null) {
                var.setSequenceType(type);
            }
            context.declareVariableBinding(var);

            if (i < input.getItemCount()) {
                var.setValue(input.itemAt(i).toSequence());
            } else {
                var.setValue(Sequence.EMPTY_SEQUENCE);
            }
            if (type != null) {
                checkVarType(var, type);
            }
        }
    }

    private void bindArrayVars(final Sequence input) throws XPathException {
        if (input.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Array destructuring requires an array, got empty sequence");
        }
        final Item item = input.itemAt(0);
        if (!Type.subTypeOf(item.getType(), Type.ARRAY_ITEM)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Array destructuring requires an array, got " +
                            Type.getTypeName(item.getType()));
        }
        final ArrayType array = (ArrayType) item;
        for (int i = 0; i < varNames.size(); i++) {
            final LocalVariable var = createVariable(varNames.get(i));
            final SequenceType type = varTypes.get(i);
            if (type != null) {
                var.setSequenceType(type);
            }
            context.declareVariableBinding(var);

            if (i < array.getSize()) {
                var.setValue(array.get(i));
            } else {
                var.setValue(Sequence.EMPTY_SEQUENCE);
            }
            if (type != null) {
                checkVarType(var, type);
            }
        }
    }

    private void bindMapVars(final Sequence input) throws XPathException {
        if (input.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Map destructuring requires a map, got empty sequence");
        }
        final Item item = input.itemAt(0);
        if (!Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Map destructuring requires a map, got " +
                            Type.getTypeName(item.getType()));
        }
        final AbstractMapType map = (AbstractMapType) item;
        for (int i = 0; i < varNames.size(); i++) {
            final QName qn = varNames.get(i);
            final LocalVariable var = createVariable(qn);
            final SequenceType type = varTypes.get(i);
            if (type != null) {
                var.setSequenceType(type);
            }
            context.declareVariableBinding(var);

            final Sequence value = map.get(new StringValue(this, qn.getLocalPart()));
            if (value != null && !value.isEmpty()) {
                var.setValue(value);
            } else {
                var.setValue(Sequence.EMPTY_SEQUENCE);
            }
            if (type != null) {
                checkVarType(var, type);
            }
        }
    }

    private void checkVarType(final LocalVariable var, final SequenceType type) throws XPathException {
        final Sequence val = var.getValue();
        if (val == null) {
            return;
        }
        final Cardinality actualCard;
        if (val.isEmpty()) {
            actualCard = Cardinality.EMPTY_SEQUENCE;
        } else if (val.hasMany()) {
            actualCard = Cardinality._MANY;
        } else {
            actualCard = Cardinality.EXACTLY_ONE;
        }
        if (!type.getCardinality().isSuperCardinalityOrEqualOf(actualCard)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid cardinality for variable $" + var.getQName() +
                            ". Expected " + type.getCardinality().getHumanDescription() +
                            ", got " + actualCard.getHumanDescription(), val);
        }
        if (!Type.subTypeOf(type.getPrimaryType(), Type.NODE) &&
                !val.isEmpty() &&
                !Type.subTypeOf(val.getItemType(), type.getPrimaryType())) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid type for variable $" + var.getQName() +
                            ". Expected " + Type.getTypeName(type.getPrimaryType()) +
                            ", got " + Type.getTypeName(val.getItemType()), val);
        }
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("let ");
        dumper.display(switch (mode) {
            case SEQUENCE -> "$(";
            case ARRAY -> "$[";
            case MAP -> "${";
        });
        for (int i = 0; i < varNames.size(); i++) {
            if (i > 0) dumper.display(", ");
            dumper.display("$").display(varNames.get(i).getLocalPart());
        }
        dumper.display(switch (mode) {
            case SEQUENCE -> ")";
            case ARRAY -> "]";
            case MAP -> "}";
        });
        dumper.display(" := ");
        inputSequence.dump(dumper);
        dumper.nl().display("return ");
        returnExpr.dump(dumper);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("let ");
        sb.append(switch (mode) {
            case SEQUENCE -> "$(";
            case ARRAY -> "$[";
            case MAP -> "${";
        });
        for (int i = 0; i < varNames.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("$").append(varNames.get(i).getLocalPart());
        }
        sb.append(switch (mode) {
            case SEQUENCE -> ")";
            case ARRAY -> "]";
            case MAP -> "}";
        });
        sb.append(" := ").append(inputSequence.toString());
        sb.append(" return ").append(returnExpr.toString());
        return sb.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        // No specific visitor method for destructure - use default
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        return new HashSet<>(varNames);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        inputSequence.resetState(postOptimization);
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }
}

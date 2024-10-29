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

import io.lacuna.bifurcan.IEntry;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * Implements the XQuery 3.1 lookup operator on maps and arrays.
 *
 * @author Wolfgang
 */
public class Lookup extends AbstractExpression {

    private Expression contextExpression;
    private Sequence keys = null;
    private Expression keyExpression = null;

    public Lookup(XQueryContext context, Expression ctxExpr) {
        super(context);
        this.contextExpression = ctxExpr;
    }

    public Lookup(XQueryContext context, Expression ctxExpr, String keyString) {
        this(context, ctxExpr);
        this.keys = new StringValue(ctxExpr, keyString);
    }

    public Lookup(XQueryContext context, Expression ctxExpr, int position) {
        this(context, ctxExpr);
        this.keys = new IntegerValue(ctxExpr, position);
    }

    public Lookup(XQueryContext context, Expression ctxExpr, Expression keyExpression) {
        this(context, ctxExpr);
        this.keyExpression = keyExpression;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo contextCopy = new AnalyzeContextInfo(contextInfo);
        if (contextExpression != null) {
            contextExpression.analyze(contextCopy);
        }
        if (keyExpression != null) {
            keyExpression.analyze(contextCopy);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        Sequence leftSeq;
        if (contextExpression == null && contextSequence == null) {
            throw new XPathException(this, ErrorCodes.XPDY0002,
                    "Lookup has nothing to select, the context item is absent");
        } else if (contextExpression == null) {
            leftSeq = contextSequence;
        } else {
            leftSeq = contextExpression.eval(contextSequence, null);
        }
        final int contextType = leftSeq.getItemType();

        // Make compatible with baseX and Saxon
        if (leftSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }


        if (!(Type.subTypeOf(contextType, Type.MAP_ITEM) || Type.subTypeOf(contextType, Type.ARRAY_ITEM))) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "expression to the left of a lookup operator needs to be a sequence of maps or arrays");
        }
        if (keyExpression != null) {
            keys = keyExpression.eval(contextSequence, null);
            if (keys.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
        }
        try {
            final ValueSequence result = new ValueSequence();
            for (SequenceIterator i = leftSeq.iterate(); i.hasNext(); ) {
                final LookupSupport item = (LookupSupport) i.nextItem();
                if (keys != null) {
                    for (SequenceIterator j = keys.iterate(); j.hasNext(); ) {
                        final AtomicValue key = j.nextItem().atomize();
                        Sequence value = item.get(key);
                        if (value != null) {
                            result.addAll(value);
                        }
                    }
                } else if(item instanceof ArrayType) {
                    result.addAll(item.keys());
                } else if(item instanceof AbstractMapType) {
                    for(final IEntry<AtomicValue, Sequence> entry : ((AbstractMapType)item)) {
                        result.addAll(entry.value());
                    }
                }
            }
            return result;
        } catch (XPathException e) {
            e.setLocation(getLine(), getColumn(), getSource());
            throw e;
        }
    }

    @Override
    public int returnsType() {
        return Type.ITEM;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        if (contextExpression != null) {
            contextExpression.dump(dumper);
        }
        dumper.display("?");
        if (keyExpression == null && keys != null && keys.getItemCount() > 0) {
            try {
                dumper.display(keys.itemAt(0).getStringValue());
            } catch (XPathException e) {
                // impossible
            }
        } else if (keyExpression != null) {
            keyExpression.dump(dumper);
        }
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (contextExpression != null) {
            contextExpression.resetState(postOptimization);
        }
        if (keyExpression != null) {
            keyExpression.resetState(postOptimization);
        }
    }

    public interface LookupSupport {

        Sequence get(AtomicValue key) throws XPathException;

        Sequence keys() throws XPathException;
    }
}

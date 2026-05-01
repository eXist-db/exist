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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

/**
 * Implements fn:function-annotations (XQuery 4.0).
 *
 * Returns annotations on a function item as a sequence of single-entry maps,
 * where each map has the annotation QName as key and annotation values as value.
 */
public class FnFunctionAnnotations extends BasicFunction {

    private static FunctionReturnSequenceType makeReturnType() {
        // XQ4 spec: map(xs:QName, xs:anyAtomicType*)*
        final FunctionReturnSequenceType rt = new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                "A sequence of single-entry maps, one per annotation");
        rt.setFunctionParamTypes(new SequenceType[] {
                new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE)
        });
        return rt;
    }

    public static final FunctionSignature FN_FUNCTION_ANNOTATIONS = new FunctionSignature(
            new QName("function-annotations", Function.BUILTIN_FUNCTION_NS),
            "Returns the annotations of a function item as a sequence of single-entry maps.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("function", Type.FUNCTION,
                            Cardinality.EXACTLY_ONE, "The function item to inspect")
            },
            makeReturnType());

    public FnFunctionAnnotations(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Item funcItem = args[0].itemAt(0);
        if (!(funcItem instanceof FunctionReference ref)) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final FunctionSignature sig = ref.getSignature();
        final Annotation[] annotations = sig.getAnnotations();
        if (annotations == null || annotations.length == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence(annotations.length);
        for (final Annotation ann : annotations) {
            final MapType map = new MapType(this, context);
            final QNameValue qnameKey = new QNameValue(this, context, ann.getName());

            // Build annotation values sequence
            final LiteralValue[] values = ann.getValue();
            if (values == null || values.length == 0) {
                map.add(qnameKey, Sequence.EMPTY_SEQUENCE);
            } else {
                final ValueSequence valSeq = new ValueSequence(values.length);
                for (final LiteralValue lv : values) {
                    valSeq.add(lv.getValue());
                }
                map.add(qnameKey, valSeq);
            }
            result.add(map);
        }
        return result;
    }
}

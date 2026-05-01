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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

/**
 * Implements XQuery 4.0 fn:deep-equal with options parameter (string or map).
 *
 * Accepts either a collation URI string (XQ3.1 compatible) or an options
 * map (XQ4.0) as the 3rd parameter. When an options map is provided,
 * validates all option keys/values and uses the options-aware comparison
 * engine in {@link DeepEqualOptions}.
 */
public class FnDeepEqualOptions extends BasicFunction {

    public static final FunctionSignature FN_DEEP_EQUAL_OPTIONS = new FunctionSignature(
            new QName("deep-equal", Function.BUILTIN_FUNCTION_NS),
            "Returns true() iff every item in $items-1 is deep-equal to the item " +
                    "at the same position in $items-2, using the specified options or collation. " +
                    "If both $items-1 and $items-2 are the empty sequence, returns true().",
            new SequenceType[]{
                    new FunctionParameterSequenceType("input1", Type.ITEM,
                            Cardinality.ZERO_OR_MORE, "The first item sequence"),
                    new FunctionParameterSequenceType("input2", Type.ITEM,
                            Cardinality.ZERO_OR_MORE, "The second item sequence"),
                    new FunctionParameterSequenceType("options", Type.ITEM,
                            Cardinality.ZERO_OR_ONE, "Collation URI string or options map")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    "true() if the sequences are deep-equal, false() otherwise"));

    public FnDeepEqualOptions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence items1 = args[0];
        final Sequence items2 = args[1];

        // Parse 3rd parameter: either string (collation) or map (options)
        if (args.length > 2 && !args[2].isEmpty()) {
            final Item optionsItem = args[2].itemAt(0);
            if (optionsItem instanceof AbstractMapType) {
                // XQ4: options map — parse, validate, and use options-aware comparison
                final DeepEqualOptions options = DeepEqualOptions.parse(
                        (AbstractMapType) optionsItem, context);
                try {
                    return BooleanValue.valueOf(options.deepEqualsSeq(items1, items2));
                } finally {
                    options.close();
                }
            } else {
                // XQ3.1 compat: string collation URI
                final Collator collator;
                try {
                    collator = context.getCollator(optionsItem.getStringValue());
                } catch (final XPathException e) {
                    // Per spec, an unsupported collation in deep-equal raises FOCH0002,
                    // not the static XQST0076 thrown by getCollator at runtime use.
                    throw new XPathException(this, ErrorCodes.FOCH0002,
                            "Unsupported collation: " + optionsItem.getStringValue(), e);
                }
                return BooleanValue.valueOf(FunDeepEqual.deepEqualsSeq(items1, items2, collator));
            }
        }

        // No 3rd parameter — use default comparison
        final Collator collator = context.getDefaultCollator();
        return BooleanValue.valueOf(FunDeepEqual.deepEqualsSeq(items1, items2, collator));
    }
}

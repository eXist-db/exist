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

import org.apache.commons.codec.binary.Base64;
import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import com.ibm.icu.text.Collator;

public class FunCollationKey extends BasicFunction {

    private static final QName FN_NAME = new QName("collation-key", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX);
    private static final String FN_DESCRIPTION =
            "Given a $value-string value and a $collection-string " +
                    "collation, generates an internal value called a collation key, with the " +
                    "property that the matching and ordering of collation " +
                    "keys reflects the matching and ordering of strings " +
                    "under the specified collation.";
    private static final FunctionReturnSequenceType FN_RETURN = new FunctionReturnSequenceType(
            Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the collation key"
    );

    public static final FunctionSignature[] FS_COLLATION_KEY_SIGNATURES = {
            new FunctionSignature(FunCollationKey.FN_NAME, FunCollationKey.FN_DESCRIPTION,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value-string", Type.STRING,
                                    Cardinality.ZERO_OR_ONE, "The value string"),
                            new FunctionParameterSequenceType("collection-string", Type.STRING,
                                    Cardinality.ZERO_OR_ONE, "The collation string")
                    }, FN_RETURN)

    };

    public FunCollationKey(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final BinaryValue result;
        final String source = (args.length >= 1) ? args[0].toString() : "";
        final Collator collator = (args.length >= 2) ? Collations.getCollationFromURI(args[1].toString()) : context.getDefaultCollator();
        result = new BinaryValueFromBinaryString(new Base64BinaryValueType(), Base64.encodeBase64String(collator.getCollationKey(source).toByteArray()));
        return result;
    }
}

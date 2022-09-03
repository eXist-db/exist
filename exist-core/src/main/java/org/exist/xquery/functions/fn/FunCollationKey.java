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
import org.exist.util.Collations;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import com.ibm.icu.text.Collator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

public class FunCollationKey extends BasicFunction {

    private static final String FN_NAME = "collation-key";
    private static final String FN_DESCRIPTION =
            "Given a $value-string value and a $collation-string " +
                    "collation, generates an internal value called a collation key, with the " +
                    "property that the matching and ordering of collation " +
                    "keys reflects the matching and ordering of strings " +
                    "under the specified collation.";
    private static final FunctionReturnSequenceType FN_RETURN = returnsOpt(Type.BASE64_BINARY, "the collation key");
    private static final FunctionParameterSequenceType PARAM_VALUE_STRING = param("value-string", Type.STRING, "The value string");
    private static final FunctionParameterSequenceType PARAM_COLLATION_STRING = param("collation-string", Type.STRING, "The collation string");
    public static final FunctionSignature[] FS_COLLATION_KEY_SIGNATURES = functionSignatures(
            FN_NAME,
            FN_DESCRIPTION,
            FN_RETURN,
            arities(
                    arity(PARAM_VALUE_STRING),
                    arity(PARAM_VALUE_STRING, PARAM_COLLATION_STRING)
            )
        );

    public FunCollationKey(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String source = (args.length >= 1) ? args[0].itemAt(0).toString() : "";
        final Collator collator = (args.length >= 2) ? Collations.getCollationFromURI(args[1].itemAt(0).toString(), ErrorCodes.FOCH0002) : null;
        final Sequence sequence;
        try (BinaryValueFromBinaryString binaryValue = new BinaryValueFromBinaryString(
                new Base64BinaryValueType(),
                Base64.encodeBase64String(
                        (collator == null) ?
                                source.getBytes(StandardCharsets.UTF_8) :
                                new String(collator.getCollationKey(source).toByteArray()).getBytes(StandardCharsets.UTF_8)))) {
            sequence = binaryValue.convertTo(new Base64BinaryValueType());
        } catch (IOException e) {
            return null;
        }
        return sequence;
    }
}

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
import org.exist.util.Collations;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.Arrays;

import static org.exist.xquery.FunctionDSL.*;

/**
 * fn:contains-token($input as xs:string*, $token as xs:string) as xs:boolean
 * fn:contains-token($input as xs:string*, $token as xs:string, $collation as xs:string) as xs:boolean
 * 
 * @author tuurma
 * @see <a href="https://www.w3.org/TR/xpath-functions-31/#func-contains-token">https://www.w3.org/TR/xpath-functions-31/#func-contains-token</a>
 */
public class FunContainsToken extends BasicFunction {
    private static final QName FS_CONTAINS_TOKEN_NAME = new QName("contains-token", Function.BUILTIN_FUNCTION_NS);

    private final static FunctionParameterSequenceType FS_INPUT = optManyParam("input", Type.STRING, "The input string");
    private final static FunctionParameterSequenceType FS_TOKEN = param("token", Type.STRING, "The token to be searched for");
    private final static FunctionParameterSequenceType FS_COLLATION = param("pattern", Type.STRING, "Collation to use");

    public final static FunctionSignature[] FS_CONTAINS_TOKEN = functionSignatures(
            FS_CONTAINS_TOKEN_NAME,
            "Determines whether or not any of the supplied strings, when tokenized at whitespace boundaries, " +
                    "contains the supplied token, under the rules of the supplied collation.",
            returns(Type.BOOLEAN, "The function returns true if and only if there is string in $input which, " +
                    "after tokenizing at whitespace boundaries, contains a token that is equal to the trimmed value of $token " +
                    "under the rules of the selected collation."),
            arities(
                    arity(FS_INPUT, FS_TOKEN),
                    arity(FS_INPUT, FS_TOKEN, FS_COLLATION)
            )
    );


    public FunContainsToken(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        if (args[0].isEmpty()) {
            return BooleanValue.FALSE;
        }

        /* for all further processing trimmed value of the token is used */
        String token = StringValue.trimWhitespace(args[1].toString());

        if (token.isEmpty()) {
            return BooleanValue.FALSE;
        }

        /* tokenize all input on whitespace*/
        ArrayList<String> fragments = new ArrayList<>();

        for (int i = 0; i < args[0].getItemCount(); i++) {
            String[] chunks = Option.tokenize(args[0].itemAt(i).getStringValue());
            fragments.addAll(Arrays.asList(chunks));
        }

        Collator collator = context.getDefaultCollator();

        if (args.length > 2 && !args[2].isEmpty()) {
            collator = context.getCollator(args[2].getStringValue());
        }

        /* return true only if some fragment matches the trimmed token under current collation */
        for (String fragment : fragments) {
            if (Collations.compare(collator, fragment, token) == Constants.EQUAL) {
                return BooleanValue.TRUE;
            }
        }
        return BooleanValue.FALSE;
    }
}

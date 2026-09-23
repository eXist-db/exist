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

import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.StringView;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.AtomicValue;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.regex.RegexUtil.*;
import static org.exist.xquery.regex.SaxonRegex.*;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @see <a href="https://www.w3.org/TR/xpath-functions-31/#func-tokenize">https://www.w3.org/TR/xpath-functions-31/#func-tokenize</a>
 */
public class FunTokenize extends BasicFunction {

    private static final QName FS_TOKENIZE_NAME = new QName("tokenize", Function.BUILTIN_FUNCTION_NS);

    private final static FunctionParameterSequenceType FS_TOKENIZE_PARAM_INPUT = optParam("input", Type.STRING, "The input string");
    private final static FunctionParameterSequenceType FS_TOKENIZE_PARAM_PATTERN = param("pattern", Type.STRING, "The tokenization pattern");

    public final static FunctionSignature[] FS_TOKENIZE = functionSignatures(
            FS_TOKENIZE_NAME,
            "Breaks the input string $input into a sequence of strings, ",
            returnsOptMany(Type.STRING, "the token sequence"),
            arities(
                arity(
                    FS_TOKENIZE_PARAM_INPUT
                ),
                arity(
                    FS_TOKENIZE_PARAM_INPUT,
                    FS_TOKENIZE_PARAM_PATTERN
                ),
                arity(
                    FS_TOKENIZE_PARAM_INPUT,
                    FS_TOKENIZE_PARAM_PATTERN,
                    param("flags", Type.STRING,"The flags")
                )
            )
    );

    public FunTokenize(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence stringArg = args[0];
        if (stringArg.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        String string = stringArg.getStringValue();
        if (string.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String flags = validateFlags(this, args.length == 3 ? args[2].itemAt(0).getStringValue() : "");
        final boolean isXQuery40 = context.getXQueryVersion() >= 40;

        final String pattern;
        if (args.length == 1) {
            // tokenize($input) is defined as tokenize(normalize-space($input), ' '). Normalizing
            // whitespace-only input leaves nothing to tokenize, and the result is the empty
            // sequence -- not a single empty string.
            string = FunNormalizeSpace.normalize(string);
            if (string.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            pattern = " ";
        } else {
            pattern = preparePattern(this, args[1].itemAt(0).getStringValue(), flags, isXQuery40);
        }

        final RegularExpression regex = compileForXQueryVersion(this,
                context.getBroker().getBrokerPool().getSaxonConfiguration(), pattern, flags, isXQuery40);
        if (matchesEmptyString(regex)) {
            throw new XPathException(this, ErrorCodes.FORX0003, "regular expression could match empty string");
        }

        return tokensOf(regex, string);
    }

    private Sequence tokensOf(final RegularExpression regex, final String string) throws XPathException {
        final ValueSequence result = new ValueSequence();
        final AtomicIterator tokens = regex.tokenize(StringView.of(string));
        AtomicValue token;
        while ((token = tokens.next()) != null) {
            result.add(new StringValue(this, token.getStringValue()));
        }
        return result;
    }


}

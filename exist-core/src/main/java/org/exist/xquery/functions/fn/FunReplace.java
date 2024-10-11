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

import java.util.ArrayList;
import java.util.List;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.Replace;
import net.sf.saxon.regex.RegularExpression;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.regex.RegexUtil.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunReplace extends BasicFunction {

	private static final QName FS_REPLACE_NAME = new QName("replace", Function.BUILTIN_FUNCTION_NS);

	private static final String FS_REPLACE_DESCRIPTION =
        "The function returns the xs:string that is obtained by replacing each non-overlapping substring " +
        "of $input that matches the given $pattern with an occurrence of the $replacement string.\n\n" + 
        "The $flags argument is interpreted in the same manner as for the fn:matches() function.\n\n" +
        "Calling the four argument version with the $flags argument set to a " +
        "zero-length string gives the same effect as using the three argument version.\n\n" +
        "If $input is the empty sequence, it is interpreted as the zero-length string.\n\nIf two overlapping " +
        "substrings of $input both match the $pattern, then only the first one (that is, the one whose first " +
        "character comes first in the $input string) is replaced.\n\nWithin the $replacement string, a variable " +
        "$N may be used to refer to the substring captured by the Nth parenthesized sub-expression in the " +
        "regular expression. For each match of the pattern, these variables are assigned the value of the " +
        "content matched by the relevant sub-expression, and the modified replacement string is then " +
        "substituted for the characters in $input that matched the pattern. $0 refers to the substring " +
        "captured by the regular expression as a whole.\n\nMore specifically, the rules are as follows, " +
        "where S is the number of parenthesized sub-expressions in the regular expression, and N is the " +
        "decimal number formed by taking all the digits that consecutively follow the $ character:\n\n" +
        "1.  If N=0, then the variable is replaced by the substring matched by the regular expression as a whole.\n\n" +
        "2.  If 1<=N<=S, then the variable is replaced by the substring captured by the Nth parenthesized " +
        "sub-expression. If the Nth parenthesized sub-expression was not matched, then the variable " +
        "is replaced by the zero-length string.\n\n" +
        "3.  If S<N<=9, then the variable is replaced by the zero-length string.\n\n" +
        "4.  Otherwise (if N>S and N>9), the last digit of N is taken to be a literal character to be " +
        "included \"as is\" in the replacement string, and the rules are reapplied using the number N " +
        "formed by stripping off this last digit.";

	private static final FunctionParameterSequenceType FS_TOKENIZE_PARAM_INPUT = optParam("input", Type.STRING, "The input string");
	private static final FunctionParameterSequenceType FS_TOKENIZE_PARAM_PATTERN = param("pattern", Type.STRING, "The pattern to match");
	private static final FunctionParameterSequenceType FS_TOKENIZE_PARAM_REPLACEMENT = param("replacement", Type.STRING, "The string to replace the pattern with");

	static final FunctionSignature [] FS_REPLACE = functionSignatures(
			FS_REPLACE_NAME,
			FS_REPLACE_DESCRIPTION,
			returns(Type.STRING, "the altered string"),
			arities(
					arity(
							FS_TOKENIZE_PARAM_INPUT,
							FS_TOKENIZE_PARAM_PATTERN,
							FS_TOKENIZE_PARAM_REPLACEMENT
					),
					arity(
							FS_TOKENIZE_PARAM_INPUT,
							FS_TOKENIZE_PARAM_PATTERN,
							FS_TOKENIZE_PARAM_REPLACEMENT,
							param("flags", Type.STRING, Cardinality.EXACTLY_ONE, "The flags")
					)
			)
	);

	public FunReplace(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
		final Sequence result;
		final Sequence stringArg = args[0];
		if (stringArg.isEmpty()) {
			result = StringValue.EMPTY_STRING;
		} else {
			final String flags;
			if (args.length == 4) {
				flags =	args[3].itemAt(0).getStringValue();
			} else {
				flags = "";
			}
    		final String string = stringArg.getStringValue();
    		final String pattern = args[1].itemAt(0).getStringValue();
			final String replace = args[2].itemAt(0).getStringValue();

			final Configuration config = context.getBroker().getBrokerPool().getSaxonConfiguration();

			final List<String> warnings = new ArrayList<>(1);

			try {
				final RegularExpression regularExpression = config.compileRegularExpression(pattern, flags, "XP30", warnings);
				if (regularExpression.matches("")) {
					throw new XPathException(this, ErrorCodes.FORX0003, "regular expression could match empty string");
				}

				//TODO(AR) cache the regular expression... might be possible through Saxon config

				if (!hasLiteral(flags)) {
					final String msg = Replace.checkReplacement(replace);
					if (msg != null) {
						throw new XPathException(this, ErrorCodes.FORX0004, msg);
					}
				}
				final CharSequence res = regularExpression.replace(string, replace);
				result = new StringValue(res.toString());

			} catch (final net.sf.saxon.trans.XPathException e) {
				switch (e.getErrorCodeLocalPart()) {
					case "FORX0001":
						throw new XPathException(this, ErrorCodes.FORX0001, e.getMessage());
					case "FORX0002":
						throw new XPathException(this, ErrorCodes.FORX0002, e.getMessage());
					case "FORX0003":
						throw new XPathException(this, ErrorCodes.FORX0003, e.getMessage());
					case "FORX0004":
						throw new XPathException(this, ErrorCodes.FORX0004, e.getMessage());
					default:
						throw new XPathException(this, e.getMessage());
				}
			}
        }
        
        return result;
	}
}

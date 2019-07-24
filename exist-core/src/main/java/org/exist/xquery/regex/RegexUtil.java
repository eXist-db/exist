/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.regex;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.StringValue;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class RegexUtil {

    /**
     * Parses the flags for an XQuery Regular Expression.
     *
     * @param context The calling expression
     * @param strFlags The XQuery Regular Expression flags.
     *
     * @return The flags for a Java Regular Expression.
     * @throws XPathException in case of invalid flag
     */
    public static int parseFlags(final Expression context, @Nullable final String strFlags) throws XPathException {
        int flags = 0;
        if(strFlags != null) {
            for (int i = 0; i < strFlags.length(); i++) {
                final char ch = strFlags.charAt(i);
                switch (ch) {
                    case 'm':
                        flags |= Pattern.MULTILINE;
                        break;

                    case 'i':
                        flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                        break;

                    case 'x':
                        flags |= Pattern.COMMENTS;
                        break;

                    case 's':
                        flags |= Pattern.DOTALL;
                        break;

                    case 'q':
                        flags |= Pattern.LITERAL;
                        break;

                    default:
                        throw new XPathException(context, ErrorCodes.FORX0001, "Invalid regular expression flag: " + ch, new StringValue(String.valueOf(ch)));
                }
            }
        }
        return flags;
    }

    /**
     * Determines if the Java Regular Expression flags have the literal flag set.
     *
     * @param flags The Java Regular Expression flags
     *
     * @return true if the literal flag is set
     */
    public static boolean hasLiteral(final int flags) {
        return (flags & Pattern.LITERAL) != 0;
    }

    /**
     * Determines if the Java Regular Expression flags have the case-insensitive flag set.
     *
     * @param flags The Java Regular Expression flags
     *
     * @return true if the case-insensitive flag is set
     */
    public static boolean hasCaseInsensitive(final int flags) {
        return (flags & Pattern.CASE_INSENSITIVE) != 0 || (flags & Pattern.UNICODE_CASE) != 0;
    }

    /**
     * Determines if the Java Regular Expression flags have the ignore-whitespace flag set.
     *
     * @param flags The Java Regular Expression flags
     *
     * @return true if the ignore-whitespace flag is set
     */
    public static boolean hasIgnoreWhitespace(final int flags) {
        return (flags & Pattern.COMMENTS) != 0;
    }

    /**
     * Translates the Regular Expression from XPath3 syntax to Java regex
     * syntax.
     *
     * @param context the context expression - used for error reporting
     * @param pattern a String containing a regular expression in the syntax of XPath Functions and Operators 3.0.
     * @param ignoreWhitespace true if whitespace is to be ignored ('x' flag)
     * @param caseBlind true if case is to be ignored ('i' flag)
     *
     * @return The Java Regular Expression
     *
     * @throws XPathException if the XQuery Regular Expression is invalid.
     */
    public static String translateRegexp(final Expression context, final String pattern, final boolean ignoreWhitespace, final boolean caseBlind) throws XPathException {
        // convert pattern to Java regex syntax
        try {
            final int xmlVersion = 11;
            return JDK15RegexTranslator.translate(pattern, xmlVersion, true, ignoreWhitespace, caseBlind);
        } catch (final RegexSyntaxException e) {
            throw new XPathException(context, ErrorCodes.FORX0002, "Conversion from XPath F&O 3.0 regular expression syntax to Java regular expression syntax failed: " + e.getMessage(), new StringValue(pattern), e);
        }
    }
}

/*  
 *  This code has been adapted from the jakarta ORO package. The original license
 *  follows below:
 *
 * ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2000 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation", "Jakarta-Oro" 
 *    must not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache" 
 *    or "Jakarta-Oro", nor may "Apache" or "Jakarta-Oro" appear in their 
 *    name, without prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 * 
 * $Id$
 */
package org.exist.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates a glob expression into a Java regular expression.
 * 
 * The following syntax is supported for glob expressions:
 * 
 * <ul>
 * <li> <b>*</b> - Matches zero or more instances of any character.  If the
 *          STAR_CANNOT_MATCH_NULL_MASK option is used, <b>*</b> matches
 *          one or more instances of any character.
 * <li> <b>?</b> - Matches one instance of any character. If the
 *          QUESTION_MATCHES_ZERO_OR_ONE_MASK option is used, <b>?</b>
 *          matches zero or one instances of any character.
 * <li> <b>[...]</b> - Matches any of characters enclosed by the brackets.
 *          <b> * </b> and <b>?</b> lose their special meanings within a
 *          character class.  Additionaly if the first character following
 *          the opening bracket is a <b>!</b> or a <b>^</b>, then any
 *          character not in the character class is matched.  A <b>-</b>
 *          between two characters can be used to denote a range.  A
 *          <b>-</b> at the beginning or end of the character class matches
 *          itself rather than referring to a range.  A <b>]</b> immediately
 *          following the opening <b>[</b> matches itself rather than
 *          indicating the end of the character class, otherwise it must be
 *          escaped with a backslash to refer to itself.
 * <li> <b>\</b> - A backslash matches itself in most situations.  But
 *          when a special character such as a <b>*</b> follows it, a
 *          backslash <em> escapes </em> the character, indicating that
 *          the special chracter should be interpreted as a normal character
 *          instead of its special meaning.
 * <li> All other characters match themselves.
 * </ul>
 * 
 * Please remember that the when you construct a Java string in Java code,
 * the backslash character is itself a special Java character, and it must
 * be double backslashed to represent single backslash in a regular
 * expression.
 * 
 * The original code is adapted from the jakarta ORO package.
 * 
 */
public class GlobToRegex {

    private static boolean __isPerl5MetaCharacter(char ch) {
        return (ch == '*' || ch == '?' || ch == '+' || ch == '[' || ch == ']'
                || ch == '(' || ch == ')' || ch == '|' || ch == '^'
                || ch == '$' || ch == '.' || ch == '{' || ch == '}' || ch == '\\');
    }

    private static boolean __isGlobMetaCharacter(char ch) {
        return (ch == '*' || ch == '?' || ch == '[' || ch == ']');
    }

    /**
     * This static method is the basic engine of the Glob PatternCompiler
     * implementation. It takes a glob expression in the form of a character
     * array and converts it into a String representation of a Perl5 pattern.
     * The method is made public so that programmers may use it for their own
     * purposes. However, the GlobCompiler compile methods work by converting
     * the glob pattern to a Perl5 pattern using this method, and then invoking
     * the compile() method of an internally stored Perl5Compiler instance.
     *
     * 
     * @param pattern
     *            A character array representation of a Glob pattern.
     * @return A String representation of a Perl5 pattern equivalent to the Glob
     *         pattern.
     */
    public static String globToRegexp(CharSequence pattern) {
		boolean inCharSet;
//        boolean starCannotMatchNull = false;
//        boolean questionMatchesZero;
        int ch;
        StringBuilder buffer;

        buffer = new StringBuilder(2 * pattern.length());
        inCharSet = false;

        char c;
        for (ch = 0; ch < pattern.length(); ch++) {
            c = pattern.charAt(ch);
            switch (c) {
                case '*':
                    if (inCharSet)
                        {buffer.append('*');}
                    else {
                        buffer.append(".*");
                    }
                    break;
                case '?':
                    if (inCharSet)
                        {buffer.append('?');}
                    else {
                        buffer.append(".?");
                    }
                    break;
                case '[':
                if (inCharSet) {
                    buffer.append("\\[");
                    break;
                }

                    inCharSet = true;
                    buffer.append(c);

                    if (ch + 1 < pattern.length()) {
                        switch (pattern.charAt(ch + 1)) {
                            case '!':
                            case '^':
                                buffer.append('^');
                                ++ch;
                                continue;
                            case ']':
                        buffer.append("]");
                                ++ch;
                                continue;
                        }
                    }
                    break;
                case ']':
                    inCharSet = false;
                    buffer.append(c);
                    break;
                case '\\':
                    buffer.append('\\');
                    if (ch == pattern.length() - 1) {
                        buffer.append('\\');
                    } else if (__isGlobMetaCharacter(pattern.charAt(ch + 1)))
                        {buffer.append(pattern.charAt(++ch));}
                    else
                        {buffer.append('\\');}
                    break;
                default:
                    if (!inCharSet && __isPerl5MetaCharacter(c))
                        {buffer.append('\\');}
                    buffer.append(c);
                    break;
            }
        }

        return buffer.toString();
    }

    /**
     * @param args the arguments
     */
    public static void main(String[] args) {
        final String glob = "[gG]enerate? ";
        final String re = globToRegexp(glob);
        
        final String str = "This is generated text";
        final Pattern pattern = Pattern.compile(re);
        final Matcher matcher = pattern.matcher(str);
		matcher.find();
        System.out.println(str.substring(matcher.start(), matcher.end()));
    }

}

/*=============================================================================

    Copyright 2009 Nikolay Ognyanov

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

=============================================================================*/

package org.exist.xpath.parser;

import org.antlr.runtime.CharStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.RecognizerSharedState;
import org.antlr.runtime.Token;

/**
 * Base class for generated lexers. Has 3 roles:
 * <ol>
 * <li> Produce tokens of custom type.</li>
 * <li> Provide control over behavior upon errors.</li>
 * <li> Check validity of character references</li>
 * </ol>
 * Note the small naming trick of this class having same simple name as
 * antlr.runtime.Lexer. The problem solved this way is lack of support
 * in ANTLR for declaration of base class for generated lexer. For the
 * sake of consistency same naming trick is used for {@link Parser} too.
 */
public abstract class Lexer
    extends org.antlr.runtime.Lexer
{

    private boolean breakOnError = true;

    public Lexer()
    {
        super();
    }

    public Lexer(CharStream input)
    {
        super(input);
    }

    public Lexer(CharStream input, RecognizerSharedState state)
    {
        super(input, state);
    }

    /**
     * Set a flag which determines error handling behavior. If the flag is
     * true then a runtime exception is thrown upon error. Default value
     * is "true".
     * 
     * @param breakOnError the value to be set
     */
    public void setBreakOnError(boolean breakOnError)
    {
        this.breakOnError = breakOnError;
    }

    /**
     * Retrieves the value of the error handling flag.
     * 
     * @return value of the flag
     */
    public boolean getBreakOnError()
    {
        return breakOnError;
    }

    /**
     * Overrides the method in the base class and produces {@link XQToken}
     * instead of CommonTokem.
     */
    @Override
    public Token emit()
    {
        Token t =
            new XQToken(input, state.type, state.channel,
                        state.tokenStartCharIndex, getCharIndex() - 1);
        t.setLine(state.tokenStartLine);
        t.setText(state.text);
        t.setCharPositionInLine(state.tokenStartCharPositionInLine);
        emit(t);
        return t;
    }

    /**
     * A placeholder for implementation of custom error message handling.
     */
    @Override
    public void emitErrorMessage(String message)
    {
        super.emitErrorMessage(message);
    }

    /**
     * Overriden in order to provide control of error handling through
     * {@link setBreakOnError}.
     */
    @Override
    public void reportError(RecognitionException e)
    {
        super.reportError(e);
        if (breakOnError) {
            throw new RuntimeException(getErrorHeader(e) + " "
                    + getErrorMessage(e, getTokenNames()), e);
        }
    }

    /**
     * Convinience method for throwing {@link XQRecognitionException}.
     * 
     * @param message the error message to be carried
     * @throws RecognitionException
     */
    protected void raiseError(String message)
        throws RecognitionException
    {
        throw new XQRecognitionException(input, message);
    }

    /**
     * Check whether a recognizer character reference references a valid Char.
     * Throws an error if this is not the case.
     * 
     * @throws RecognitionException
     */
    protected void checkCharRef()
        throws RecognitionException
    {
        String ref = getText();
        int length = ref.length();
        int start = 0;
        for (int i = length - 1; i >= 0; i--) {
            if (ref.charAt(i) == '&') {
                start = i;
                break;
            }
        }
        boolean isHex = ref.charAt(start + 2) == 'x';
        int value = 0;
        try {
            if (isHex) {
                value =
                    Integer.parseInt(ref.substring(start + 3, length - 1), 16);
            }
            else {
                value = Integer.parseInt(ref.substring(start + 2, length - 1));
            }
        }
        catch (RuntimeException e) {
            raiseError("Invalid character constant '"
                    + ref.substring(start - 2) + ".'");
        }

        if ((value == '\n') || (value == '\r') || (value == '\t')
                || (value >= '\u0020') && (value <= '\uD7FF')
                || (value >= '\uE000') && (value <= '\uFFFD')
                || (value >= 65536) && (value <= 1114111))
        //                  #x10000              #x10FFFF
        {
            // OK
        }
        else {
            raiseError("Invalid character constant '" + ref.substring(start)
                    + ".'");
        }
    }
}

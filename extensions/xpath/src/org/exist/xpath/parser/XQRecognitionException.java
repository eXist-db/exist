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

import org.antlr.runtime.IntStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenStream;

/**
 * Recongintion exception class thrown by "add-on" parsing procedures
 * called by the generated parser to do some checks and parsing it does
 * not handle automatically. In such cases if error is detected,it must
 * be attributed to the current already recognized token and not to the 
 * lookahead as standard ANTLR exceptions do.
 */
public class XQRecognitionException
    extends RecognitionException
{
    private static final long serialVersionUID = 1L;
    private String            message;

    public XQRecognitionException(IntStream input, String message)
    {
        super(input);
        this.message = message;
        if (input instanceof TokenStream) {
            this.token = ((TokenStream) input).get(input.index());
            this.line = token.getLine();
            this.charPositionInLine = token.getCharPositionInLine();
        }
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    @Override
    public String toString()
    {
        return message;
    }
}

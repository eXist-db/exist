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
import org.antlr.runtime.CommonToken;

/**
 * In XQuery there is need to sometimes check that a token is
 * or is not preceded by white space. This is cumbersome to
 * do entirelly automatically in the generated parser and lexers, 
 * so it is done in part by "add-on" procedures but we need to 
 * keep around appropriate information. This custom token class 
 * stores it. It could as well store complete content of preceding 
 * white spaces (including comments) if the need be (to recover 
 * full text of query).
 */
public class XQToken
    extends CommonToken
{
    private static final long serialVersionUID = 2315498543309487252L;
    protected boolean         spaceBefore;

    public XQToken(CharStream input, int type, int channel, int start, int stop)
    {
        super(input, type, channel, start, stop);
    }

    public XQToken(int type, java.lang.String text)
    {
        super(type, text);
    }

}

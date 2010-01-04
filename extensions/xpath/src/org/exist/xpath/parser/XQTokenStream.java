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

import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.Token;
import org.antlr.runtime.TokenSource;
import org.antlr.runtime.TokenStream;

/**
 * A replacement for CommonTokenStream. Needed because CommonTokenStream
 * is too greedy in consuming tokens from the lexer and therefore does
 * not allow for switching of lexers on the fly which is done in {@link Parser}.
 */
public class XQTokenStream
    implements TokenStream
{
    private TokenSource tokenSource;
    private int         channel     = Token.DEFAULT_CHANNEL;
    private List<Token> tokens      = new ArrayList<Token>();
    private int         index       = -1;
    private int         lastMarker;
    private String[]    tokenNames;
    boolean             spaceBefore = false;

    public XQTokenStream(TokenSource tokenSource)
    {
        this.tokenSource = tokenSource;
    }

    @Override
    public TokenSource getTokenSource()
    {
        return tokenSource;
    }

    public void setTokenSource(TokenSource tokenSource)
    {
        this.tokenSource = tokenSource;
    }

    @Override
    public String getSourceName()
    {
        return tokenSource.getSourceName();
    }

    @Override
    public int index()
    {
        return index;
    }

    @Override
    public Token get(int i)
    {
        return tokens.get(i);
    }

    @Override
    public Token LT(int offset)
    {
        if (offset == 0) {
            return null;
        }
        else if (offset < 0) {
            if (index + offset < 0) {
                return null;
            }
            if (!ensureSize(index + offset + 1))
                return Token.EOF_TOKEN;
            return tokens.get(index + offset);
        }

        if (!ensureSize(index + offset + 1))
            return Token.EOF_TOKEN;
        return tokens.get(index + offset);
    }

    @Override
    public int LA(int k)
    {
        return LT(k).getType();
    }

    @Override
    public void consume()
    {
        index++;
    }

    @Override
    public int mark()
    {
        lastMarker = index;
        return lastMarker;
    }

    @Override
    public void release(int marker)
    {
    }

    @Override
    public void rewind()
    {
        seek(lastMarker);
    }

    @Override
    public void rewind(int marker)
    {
        seek(marker);
    }

    @Override
    public void seek(int index)
    {
        this.index = index;
    }

    @Override
    public int size()
    {
        return tokens.size();
    }

    @Override
    public String toString(int start, int stop)
    {
        ensureSize(stop);
        int limit = stop <= size() ? stop : size();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < limit; i++) {
            sb.append(get(i));
        }
        return sb.toString();
    }

    @Override
    public String toString(Token start, Token end)
    {
        if (start == null || end == null) {
            return null;
        }
        return toString(start.getTokenIndex(), end.getTokenIndex());
    }

    public String[] getTokenNames()
    {
        return tokenNames;
    }

    public void setTokenNames(String[] tokenNames)
    {
        this.tokenNames = tokenNames;
    }

    private boolean ensureSize(int size)
    {
        if (size < tokens.size()) {
            return true;
        }
        while (tokens.size() < size) {
            Token nextToken = tokenSource.nextToken();
            if (nextToken == Token.EOF_TOKEN) {
                return false;
            }
            if (nextToken.getChannel() == channel) {
                ((XQToken) nextToken).spaceBefore = spaceBefore;
                spaceBefore = false;
                tokens.add(nextToken);
                /*
                if (tokenNames != null) {
                    System.out.println(tokenNames[nextToken.getType()] + "("
                            + nextToken.getType() + ") => "
                            + nextToken.getText());
                }
                else {
                    System.out.println("Token " + nextToken.getType() + " => "
                            + nextToken.getText());
                }
                */
            }
            else {
                spaceBefore = true;
            }
        }
        return true;
    }
}

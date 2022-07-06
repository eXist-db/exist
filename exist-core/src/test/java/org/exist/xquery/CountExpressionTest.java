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
package org.exist.xquery;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class CountExpressionTest {

    @Test
    public void countTest() throws RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for $p in $products\n" +
                "order by $p/sales descending\n" +
                "count $rank\n" +
                "where $rank <= 3\n" +
                "return\n" +
                "   <product rank=\"{$rank}\">\n" +
                "      {$p/name, $p/sales}\n" +
                "   </product>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        final XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
        final XQueryParser xparser = new XQueryParser(lexer);
        xparser.xpath();
        if (xparser.foundErrors()) {
            fail(xparser.getErrorMessage());
            return;
        }

        final XQueryAST ast = (XQueryAST) xparser.getAST();

        final XQueryTreeParser treeParser = new XQueryTreeParser(context);
        final PathExpr expr = new PathExpr(context);
        treeParser.xpath(ast, expr);
        if (treeParser.foundErrors()) {
            fail(treeParser.getErrorMessage());
            return;
        }

        // count keyword
        assertEquals(143, ast.getNextSibling().getFirstChild().getNextSibling().getNextSibling().getType());
        // rank variable binding
        assertEquals(20, ast.getNextSibling().getFirstChild().getNextSibling().getNextSibling().getFirstChild().getType());
    }
}

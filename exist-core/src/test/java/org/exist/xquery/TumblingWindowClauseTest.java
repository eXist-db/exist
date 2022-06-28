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
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.util.LockException;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.*;

/**
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class TumblingWindowClauseTest {

    @Test
    public void query() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, RecognitionException, XPathException, TokenStreamException {
        final String query =
                "xquery version \"3.0\";\n" +
                        "for tumbling window \n" +
                        "return <p>1</p>";

        // parse the query into the internal syntax tree
        final XQueryLexer lexer = new XQueryLexer(new StringReader(query));
        final XQueryParser xparser = new XQueryParser(lexer);
        xparser.xpath();
        if (xparser.foundErrors()) {
            fail(xparser.getErrorMessage());
        }
    }
}

/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
package org.exist.xquery.modules.simpleql;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.StringReader;

public class ParseSimpleQL extends BasicFunction {

	@SuppressWarnings("unused")
	private static final Logger logger = LogManager.getLogger(ParseSimpleQL.class);
	
    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("parse-simpleql", SimpleQLModule.NAMESPACE_URI, SimpleQLModule.PREFIX),
            "Translates expressions in a simple query language to an XPath expression. A single search term " +
            "is translated into '. &= term', 'and'/'or' used to combine terms, quotes define a phrase and are translated " +
            "into near(., 'quoted terms').",
            new SequenceType[] { new FunctionParameterSequenceType("expression", Type.STRING, Cardinality.ZERO_OR_ONE, "The expression to parse")},
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the result"));
    
    public ParseSimpleQL(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        String query = args[0].getStringValue();
        SimpleQLLexer lexer = new SimpleQLLexer(new StringReader(query));
        SimpleQLParser parser = new SimpleQLParser(lexer);
        try {
            return new StringValue(parser.expr());
        } catch (RecognitionException | TokenStreamException e) {
            throw new XPathException(this, "An error occurred while parsing the query expression: " + e.getMessage(), e);
        }
    }
}

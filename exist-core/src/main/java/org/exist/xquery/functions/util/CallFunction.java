/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class CallFunction extends Function {
	
	protected static final Logger logger = LogManager.getLogger(CallFunction.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("call", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Invokes a first-class function reference created by util:function. The function " +
            "to be called is passed as the first argument. All remaining arguments are " +
            "forwarded to the called function.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function-reference", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to ba called"),
                new FunctionParameterSequenceType("parameters", Type.ITEM, Cardinality.ZERO_OR_MORE, "The parameters to be passed into the function")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results from the function called"),
            true
        );

    public CallFunction(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
    	
        final Sequence arg0 = getArgument(0).eval(contextSequence, contextItem);
        if(arg0.getCardinality() != Cardinality.EXACTLY_ONE)
            {throw new XPathException(this, "Expected exactly one item for first argument");}
        final Item item0 = arg0.itemAt(0);
        if(item0.getType() != Type.FUNCTION_REFERENCE)
            {throw new XPathException(this, "Type error: expected function, got " + Type.getTypeName(item0.getType()));}
        try (final FunctionReference ref = (FunctionReference)item0) {

            // pass the remaining parameters to the function call
            final List<Expression> params = new ArrayList<Expression>(getArgumentCount() - 1);
            for (int i = 1; i < getArgumentCount(); i++) {
                params.add(getArgument(i));
            }
            ref.setArguments(params);
            ref.analyze(new AnalyzeContextInfo(this, 0));
            // Evaluate the function
            return ref.eval(contextSequence);
        }
    }
}

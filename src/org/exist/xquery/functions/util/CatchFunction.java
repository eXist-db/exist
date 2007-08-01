/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.LocalVariable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;


/**
 * @author wolf
 */
public class CatchFunction extends Function {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("catch", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"This function corresponds to a try-catch statement in Java. The code block " +
			"in $b will be put inside a try-catch statement. If an exception is thrown while executing " +
			"$b, the function checks the name of the exception and calls $c if it matches one of " +
			"the fully qualified Java class names specified in $a",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
    
    /**
     * @param context
     */
    public CatchFunction(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Function#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        Sequence exceptionClasses = getArgument(0).eval(contextSequence, contextItem);
        try {
//            context.pushDocumentContext();
            LocalVariable mark = context.markLocalVariables(false);
            try {
                return getArgument(1).eval(contextSequence, contextItem);
            } finally {
//                context.popDocumentContext();
                context.popLocalVariables(mark);
            }
        } catch(Exception e) {
        	LOG.debug("Caught exception in util:catch: " + e.getMessage());
            if (!(e instanceof XPathException))
                LOG.warn("Exception: " + e.getMessage(), e);
//            context.popDocumentContext();
            context.getWatchDog().reset();
            for(SequenceIterator i = exceptionClasses.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                try {
                    Class exClass = Class.forName(next.getStringValue());
                    if(exClass.getName().equals(e.getClass().getName()) || exClass.isInstance(e)) {
                        LOG.debug("Calling exception handler to process " + e.getClass().getName());
                        UtilModule myModule =
                			(UtilModule) context.getModule(UtilModule.NAMESPACE_URI);
                        myModule.declareVariable(UtilModule.EXCEPTION_QNAME, new StringValue(e.getClass().getName()));
                        myModule.declareVariable(UtilModule.EXCEPTION_MESSAGE_QNAME, new StringValue(e.getMessage()));
                        return getArgument(2).eval(contextSequence, contextItem);
                    }
                } catch (Exception e2) {
                    if (e2 instanceof XPathException)
                        throw (XPathException) e2;
                    else
                        throw new XPathException(getASTNode(), "Error in exception handler: " + e2.getMessage(), e);
                }
            }
            // this type of exception is not caught: throw again
            if(e instanceof XPathException)
                throw (XPathException)e;
            throw new XPathException(e);
        }
    }

    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#returnsType()
     */
    public int returnsType() {
        return getArgument(1).returnsType();
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#getCardinality()
     */
    public int getCardinality() {
        return getArgument(1).getCardinality();
    }
}

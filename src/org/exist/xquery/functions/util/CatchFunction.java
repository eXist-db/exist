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
			new QName("catch", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX),
			"Catches the specified exceptions.",
			new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
    
    /**
     * @param context
     * @param signature
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
            context.pushDocumentContext();
            try {
                Sequence result = getArgument(1).eval(contextSequence, contextItem);
                return result;
            } finally {
                context.popDocumentContext();
            }
        } catch(Exception e) {
            context.popDocumentContext();
            context.getWatchDog().reset();
            for(SequenceIterator i = exceptionClasses.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                try {
                    Class exClass = Class.forName(next.getStringValue());
                    if(exClass.getName().equals(e.getClass().getName()) || exClass.isInstance(e)) {
                        LOG.debug("Calling exception handler to process " + e.getClass().getName());
                        ModuleImpl myModule =
                			(ModuleImpl) context.getModule(ModuleImpl.NAMESPACE_URI);
                        QName exQname = new QName("exception", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX);
                        myModule.declareVariable(exQname, new StringValue(e.getClass().getName()));
                        return getArgument(2).eval(contextSequence, contextItem);
                    }
                } catch (ClassNotFoundException e1) {
                    LOG.warn(e1.getMessage(), e1);
                } catch (XPathException e2) {
                    LOG.warn(e2);
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

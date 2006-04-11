/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author Dannes Wessels
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunTrace extends BasicFunction {
    
    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("trace", Function.BUILTIN_FUNCTION_NS),
            "This function is intended to be used in debugging queries by "
            +"providing a trace of their execution. The input $a is "
            +"returned, unchanged, as the result of the function. "
            +"In addition, the inputs $a, converted to an xs:string, "
            +"and $b is directed to a trace data set in the eXist log files.",
            new SequenceType[] {
                new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
        );
    
    public FunTrace(XQueryContext context) {
        super(context, signature);
    }
    
/*
 * (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(Sequence[], Sequence)
 */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        // TODO Add TRACE log statements using log4j
        // TODO Figure out why XQTS still does not work
        // TODO Remove unneeded comments
        
        LOG.debug("start");
        LOG.debug("string="+args[1].getStringValue());
        
        Sequence result ;
        
        if(args[0].isEmpty()){
            LOG.debug("empty sequence");
            result = Sequence.EMPTY_SEQUENCE;
            
        } else {
            // Copy all Items from input to output sequence
            LOG.debug("create sequence");
            result = new ValueSequence(); 
            for(SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                
                LOG.debug("Add item '" + next.getStringValue() + "'");
                result.add(next);
            }
        }
        
        LOG.debug("end");
        return result;
    }
}

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
 */
package org.exist.xquery.modules.math;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Dannes Wessels
 */
public class ComplexFunctions extends Function {
    
    public final static FunctionSignature signature[] = {
        // Functions, two parameters
        new FunctionSignature(
                new QName("atan2", MathModule.NAMESPACE_URI),
                "Returns the angle (radians) from the X axis to a point ($b,$a).",
                new SequenceType[] {
                    new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
                },
                new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
                ),
        new FunctionSignature(
                new QName("power", MathModule.NAMESPACE_URI),
                "Returns the value of $a raised to the power of $b.",
                new SequenceType[] {
                    new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
                },
                new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
                )
    };
    
    /**
     * @param context
     */
    public ComplexFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
         */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
        if(contextItem != null)
            contextSequence = contextItem.toSequence();
        
        Sequence result;
        double calcValue=0;
        String functionName = getSignature().getName().getLocalName();
        
        Sequence seqA = getArgument(0).eval(contextSequence, contextItem);
        NumericValue valueA = (NumericValue)seqA.itemAt(0).convertTo(Type.DOUBLE);
        
        Sequence seqB = getArgument(1).eval(contextSequence, contextItem);
        NumericValue valueB = (NumericValue)seqB.itemAt(0).convertTo(Type.DOUBLE);
        
        if("atan2".equals(functionName)) {
            calcValue=Math.atan2(valueB.getDouble(),valueA.getDouble());
            
        } else if("power".equals(functionName)) {
            calcValue=Math.pow(valueA.getDouble(), valueB.getDouble());
            
        } else {
            // DWES: can this be thrown here?
            throw new XPathException("Function not found.");
        }
        result=new DoubleValue(calcValue);
        
        
        if (context.getProfiler().isEnabled())
            context.getProfiler().end(this, "", result);
        
        return result;
    }
    
}

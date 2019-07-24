/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.xquery.functions.math;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *  Class containing math functions that accept two parameters.
 *
 * @author Dannes Wessels
 */
public class TwoParamFunctions extends BasicFunction {
	
    @SuppressWarnings("unused")
    private static final Logger logger = LogManager.getLogger(TwoParamFunctions.class);
    
    public static final String ATAN2 = "atan2";
    public static final String POW = "pow";
    
    
    public final static FunctionSignature FNS_ATAN2 = new FunctionSignature(
        new QName(ATAN2, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the angle in radians subtended at the origin by the point on a "
        + "plane with coordinates (x, y) and the positive x-axis, the result being in the range -π to +π.",
        new SequenceType[] {
            new FunctionParameterSequenceType("y", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The y coordinate"),
            new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The x coordinate")
        },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the theta component of the point (r, theta) in polar coordinates that corresponds to the point (x, y) in Cartesian coordinates.")
    );
        
    public final static FunctionSignature FNS_POW = new FunctionSignature(
        new QName(POW, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the result of raising the first argument to the power of the second.",
        new SequenceType[] {
            new FunctionParameterSequenceType("value", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The value"),
            new FunctionParameterSequenceType("power", Type.NUMBER, Cardinality.EXACTLY_ONE, "The power to raise the value to")
        },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the result")
    );

    public TwoParamFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null){
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
        }
        
       
        Sequence result;
        double calcValue=0;
        final String functionName = getSignature().getName().getLocalPart();
        
        final Sequence seqA = args[0].convertTo(Type.DOUBLE);
        final NumericValue valueA = (NumericValue)seqA.itemAt(0).convertTo(Type.DOUBLE);
        
        final Sequence seqB = args[1].convertTo(Type.DOUBLE);
        final NumericValue valueB = (NumericValue)seqB.itemAt(0).convertTo(Type.DOUBLE);
        
        if(ATAN2.equals(functionName)) {
            calcValue = Math.atan2(valueA.getDouble(), valueB.getDouble());
            
        } else if(POW.equals(functionName)) {            
            calcValue=Math.pow(valueA.getDouble(), valueB.getDouble());
            
        } else {
            throw new XPathException(this, "Function "+functionName+" not found.");
        }
        result=new DoubleValue(calcValue);
        
        
        if (context.getProfiler().isEnabled()){
            context.getProfiler().end(this, "", result);
        }
        
        return result;
    }
    
}

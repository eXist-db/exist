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

import org.apache.log4j.Logger;
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
 * Class containing math functions that accept one parameter.
 *
 * @author Dannes Wessels
 */
public class OneParamFunctions extends BasicFunction {
	
	@SuppressWarnings("unused")
	private static final Logger logger = Logger.getLogger(OneParamFunctions.class);
    
    public final static FunctionSignature signature[] = {
        new FunctionSignature(
                new QName("acos", MathModule.NAMESPACE_URI),
                "Returns the arc cosine of the argument, the result being in the range zero to +π radians.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the result")
                ),
        new FunctionSignature(
                new QName("asin", MathModule.NAMESPACE_URI),
                "Returns the arc sine of the argument, the result being in the range -π/2 to +π/2 radians.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "result")
                ),
        new FunctionSignature(
                new QName("atan", MathModule.NAMESPACE_URI),
                "Returns the arc tangent of the argument, the result being in the range -π/2 to +π/2 radians.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the result")
                ),
        new FunctionSignature(
                new QName("cos", MathModule.NAMESPACE_URI),
                "Returns the cosine of the argument, expressed in radians.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the cosine")
                ),
        new FunctionSignature(
                new QName("exp", MathModule.NAMESPACE_URI),
                "Calculates e (the Euler Constant) raised to the power of $arg",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "e (the Euler Constant) raised to the power of a value or expression")
                ),
        new FunctionSignature( // NEW
                new QName("exp10", MathModule.NAMESPACE_URI),
                "Calculates 10 raised to the power of $arg",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "e (the Euler Constant) raised to the power of a value or expression")
                ),
        new FunctionSignature(
                new QName("log", MathModule.NAMESPACE_URI),
                "Returns the natural logarithm of the argument.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the log")
                ),
        new FunctionSignature( // NEW
                new QName("log10", MathModule.NAMESPACE_URI),
                "Returns the base-ten logarithm of the argument.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the log")
                ),
        new FunctionSignature(
                new QName("sin", MathModule.NAMESPACE_URI),
                "Returns the sine of the argument, expressed in radians.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the sine")
                ),
        new FunctionSignature(
                new QName("sqrt", MathModule.NAMESPACE_URI),
                "Returns the non-negative square root of the argument.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the square root of $x")
                ),
        new FunctionSignature(
                new QName("tan", MathModule.NAMESPACE_URI),
                "Returns the tangent of the argument, expressed in radians.",
                new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The radians") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the tangent")
                ),
    };
    
    /**
     * @param context
     */
    public OneParamFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        Sequence seq = args[0].convertTo(Type.DOUBLE);
        NumericValue value = (NumericValue)seq.itemAt(0).convertTo(Type.DOUBLE);

        if(seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
            
        } else {          
            double calcValue=0;
            String functionName = getSignature().getName().getLocalName();
            if("acos".equals(functionName)) {
                calcValue=Math.acos(value.getDouble());
                
            } else if("asin".equals(functionName)) {
                calcValue=Math.asin(value.getDouble());
                
            } else if("atan".equals(functionName)) {
                calcValue=Math.atan(value.getDouble());
                
            } else if("cos".equals(functionName)) {
                calcValue=Math.cos(value.getDouble());
                
            } else if("exp".equals(functionName)) {
                calcValue=Math.exp(value.getDouble());
                
            } else if("exp10".equals(functionName)) {
                calcValue=Math.pow(10.0d, calcValue);
                
            } else if("log".equals(functionName)) {
                calcValue=Math.log(value.getDouble());
                
            } else if("log10".equals(functionName)) {
                calcValue=Math.log10(value.getDouble());
                
            } else if("sin".equals(functionName)) {
                calcValue=Math.sin(value.getDouble());
                
            } else if("sqrt".equals(functionName)) {
                calcValue=Math.sqrt(value.getDouble());
                
            } else if("tan".equals(functionName)) {
                calcValue=Math.tan(value.getDouble());
                
            } else {
                throw new XPathException(this, "Function "+functionName+" not found.");
            }
            result=new DoubleValue(calcValue);
        }
        
        if (context.getProfiler().isEnabled()){
            context.getProfiler().end(this, "", result);
        }
        
        return result;
    }
    
}

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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.math;

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
 * Class containing math functions that accept one parameter.
 *
 * @author Dannes Wessels
 */
public class OneParamFunctions extends BasicFunction {
	
	@SuppressWarnings("unused")
	private static final Logger logger = LogManager.getLogger(OneParamFunctions.class);
    
    public final static FunctionSignature signature[] = {
        new FunctionSignature(
                new QName("abs", MathModule.NAMESPACE_URI),
                "Calculates the absolute value (distance from zero) of a value or expression",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The value to return the absolute value of") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the absolute value (distance from zero) of a value or expression")
                ),
        new FunctionSignature(
                new QName("acos", MathModule.NAMESPACE_URI),
                "Returns the arc cosine of an angle, in the range of 0.0 through pi.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the result"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_ACOS
                ),
        new FunctionSignature(
                new QName("asin", MathModule.NAMESPACE_URI),
                "Returns the arc sine of an angle, in the range of -pi/2 through pi/2.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "result"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_ASIN
                ),
        new FunctionSignature(
                new QName("atan", MathModule.NAMESPACE_URI),
                "Returns the arc tangent of an angle, in the range of -pi/2 through pi/2.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the result"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_ATAN
                ),
        new FunctionSignature(
                new QName("ceil", MathModule.NAMESPACE_URI),
                "Returns the smallest (closest to negative infinity) value that is not less than the argument and is equal to a mathematical integer.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "result")
                ),
        new FunctionSignature(
                new QName("cos", MathModule.NAMESPACE_URI),
                "Returns the trigonometric cosine of an angle.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the cosine"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_COS
                ),
        new FunctionSignature(
                new QName("exp", MathModule.NAMESPACE_URI),
                "Calculates e (the Euler Constant) raised to the power of a value or expression",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "e (the Euler Constant) raised to the power of a value or expression"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_EXP
                ),
        new FunctionSignature(
                new QName("floor", MathModule.NAMESPACE_URI),
                "Returns the largest (closest to positive infinity) value that is not greater than the argument and is equal to a mathematical integer.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the floor value")
                ),
        new FunctionSignature(
                new QName("log", MathModule.NAMESPACE_URI),
                "Returns the natural logarithm (base e) of a number.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the log"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_LOG
                ),
        new FunctionSignature(
                new QName("round", MathModule.NAMESPACE_URI),
                "Returns the double value that is closest to a integer.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the rounded value")
                ),
        new FunctionSignature(
                new QName("sin", MathModule.NAMESPACE_URI),
                "Returns the trigonometric sine of an angle.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the sine"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_SIN
                ),
        new FunctionSignature(
                new QName("sqrt", MathModule.NAMESPACE_URI),
                "Returns the correctly rounded positive square root of a number.",
                new SequenceType[] { new FunctionParameterSequenceType("x", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The input number") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the square root of $x"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_SQRT
                ),
        new FunctionSignature(
                new QName("tan", MathModule.NAMESPACE_URI),
                "Returns the tangent of the number passed as an argument in radians.",
                new SequenceType[] { new FunctionParameterSequenceType("radians", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The radians") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the tangent"),
                org.exist.xquery.functions.math.OneParamFunctions.FNS_TAN
                ),
        new FunctionSignature(
                new QName("degrees", MathModule.NAMESPACE_URI),
                "Converts angle in radians to degrees.",
                new SequenceType[] { new FunctionParameterSequenceType("radians", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The radians") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the degrees")
                ),
        new FunctionSignature(
                new QName("radians", MathModule.NAMESPACE_URI),
                "Converts angle in degrees to radians.",
                new SequenceType[] { new FunctionParameterSequenceType("degrees", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The degrees") },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the radians")
                )
    };
    
    /**
     * @param context
     */
    public OneParamFunctions(XQueryContext context, FunctionSignature signature) {
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
        Sequence seq = args[0].convertTo(Type.DOUBLE);
        NumericValue value = (NumericValue)seq.itemAt(0).convertTo(Type.DOUBLE);

        if(seq.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {          
            double calcValue=0;
            String functionName = getSignature().getName().getLocalPart();
            if("abs".equals(functionName)) {
                calcValue=Math.abs(value.getDouble());
                
            } else if("acos".equals(functionName)) {
                calcValue=Math.acos(value.getDouble());
                
            } else if("asin".equals(functionName)) {
                calcValue=Math.asin(value.getDouble());
                
            } else if("atan".equals(functionName)) {
                calcValue=Math.atan(value.getDouble());
                
            } else if("ceil".equals(functionName)) {
                calcValue=Math.ceil(value.getDouble());
                
            } else if("cos".equals(functionName)) {
                calcValue=Math.cos(value.getDouble());
                
            } else if("exp".equals(functionName)) {
                calcValue=Math.exp(value.getDouble());
                
            } else if("floor".equals(functionName)) {
                calcValue=Math.floor(value.getDouble());
                
            } else if("log".equals(functionName)) {
                calcValue=Math.log(value.getDouble());
                
            } else if("round".equals(functionName)) {
                calcValue=Math.rint(value.getDouble());
                
            } else if("sin".equals(functionName)) {
                calcValue=Math.sin(value.getDouble());
                
            } else if("sqrt".equals(functionName)) {
                calcValue=Math.sqrt(value.getDouble());
                
            } else if("tan".equals(functionName)) {
                calcValue=Math.tan(value.getDouble());
                
            } else if("degrees".equals(functionName)) {
                calcValue=Math.toDegrees(value.getDouble());
                
            } else if("radians".equals(functionName)) {
                calcValue=Math.toRadians(value.getDouble());
                
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

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
	
	//private static final Logger logger = LogManager.getLogger(OneParamFunctions.class);
    
    public static final String ACOS = "acos";
    public static final String ASIN = "asin";
    public static final String ATAN = "atan";
    public static final String COS = "cos";
    public static final String EXP = "exp";
    public static final String EXP10 = "exp10";
    public static final String LOG = "log";
    public static final String LOG10 = "log10";
    public static final String SIN = "sin";
    public static final String SQRT = "sqrt";
    public static final String TAN = "tan";
    
    public final static FunctionSignature FNS_ACOS = new FunctionSignature(
        new QName(ACOS, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the arc cosine of the argument, the result being in the range zero to +π radians.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the result")
    );
                        
    public final static FunctionSignature FNS_ASIN = new FunctionSignature(
        new QName(ASIN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the arc sine of the argument, the result being in the range -π/2 to +π/2 radians.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "result")
    );
    
    public final static FunctionSignature FNS_ATAN = new FunctionSignature(
        new QName(ATAN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the arc tangent of the argument, the result being in the range -π/2 to +π/2 radians.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the result")
    );
    
    public final static FunctionSignature FNS_COS = new FunctionSignature(
        new QName(COS, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the cosine of the argument, expressed in radians.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the cosine")
    );
    
    public final static FunctionSignature FNS_EXP = new FunctionSignature(
        new QName(EXP, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Calculates e (the Euler Constant) raised to the power of $arg",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "e (the Euler Constant) raised to the power of a value or expression")
    );
                
    public final static FunctionSignature FNS_EXP10 = new FunctionSignature( // NEW
        new QName(EXP10, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Calculates 10 raised to the power of $arg",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "e (the Euler Constant) raised to the power of a value or expression")
    );
        
    public final static FunctionSignature FNS_LOG = new FunctionSignature(
        new QName(LOG, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the natural logarithm of the argument.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the log")
    );
        
    public final static FunctionSignature FNS_LOG10 = new FunctionSignature( // NEW
        new QName(LOG10, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the base-ten logarithm of the argument.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the log")
    );
        
    public final static FunctionSignature FNS_SIN = new FunctionSignature(
        new QName(SIN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the sine of the argument, expressed in radians.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the sine")
    );
        
    public final static FunctionSignature FNS_SQRT = new FunctionSignature(
        new QName(SQRT, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the non-negative square root of the argument.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the square root of $x")
    );
    
    public final static FunctionSignature FNS_TAN = new FunctionSignature(
        new QName(TAN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the tangent of the argument, expressed in radians.",
        new SequenceType[] { new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The radians") },
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the tangent")
    );

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
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
        }
        
        Sequence result;
        final Sequence seq = args[0].convertTo(Type.DOUBLE);
        final NumericValue value = (NumericValue) seq.itemAt(0).convertTo(Type.DOUBLE);

        if (seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
            
        } else {          
            double calcValue = 0;
            final String functionName = getSignature().getName().getLocalPart();
            if (ACOS.equals(functionName)) {
                calcValue = Math.acos(value.getDouble());
                
            } else if (ASIN.equals(functionName)) {
                calcValue = Math.asin(value.getDouble());
                
            } else if (ATAN.equals(functionName)) {
                calcValue = Math.atan(value.getDouble());
                
            } else if (COS.equals(functionName)) {
                calcValue = Math.cos(value.getDouble());
                
            } else if (EXP.equals(functionName)) {
                calcValue = Math.exp(value.getDouble());
                
            } else if (EXP10.equals(functionName)) {
                calcValue = Math.pow(10.0d, value.getDouble());
                
            } else if (LOG.equals(functionName)) {
                calcValue = Math.log(value.getDouble());
                
            } else if (LOG10.equals(functionName)) {
                calcValue = Math.log10(value.getDouble());
                
            } else if (SIN.equals(functionName)) {
                calcValue = Math.sin(value.getDouble());
                
            } else if (SQRT.equals(functionName)) {
                calcValue = Math.sqrt(value.getDouble());
                
            } else if (TAN.equals(functionName)) {
                calcValue = Math.tan(value.getDouble());
                
            } else {
                throw new XPathException(this, "Function " + functionName + " not found.");
            }
            result = new DoubleValue(calcValue);
        }
        
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        
        return result;
    }
    
}

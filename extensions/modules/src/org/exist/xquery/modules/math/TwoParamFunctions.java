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
package org.exist.xquery.modules.math;

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
 *  Class containing math functions that accept two parameters.
 *
 * @author Dannes Wessels
 */
public class TwoParamFunctions extends BasicFunction {
	
	@SuppressWarnings("unused")
	private static final Logger logger = Logger.getLogger(TwoParamFunctions.class);
    
    public final static FunctionSignature signature[] = {
        new FunctionSignature(
                new QName("functionname", MathModule.NAMESPACE_URI),
                "Description",
                new SequenceType[] {
                    new FunctionParameterSequenceType("arg1", Type.DOUBLE, Cardinality.EXACTLY_ONE, "About arg1"),
                    new FunctionParameterSequenceType("arg2", Type.DOUBLE, Cardinality.EXACTLY_ONE, "About arg2")
                },
                new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "About result.")
                ),

    };
    
    /**
     * @param context
     */
    public TwoParamFunctions(XQueryContext context, FunctionSignature signature) {
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
        double calcValue=0;
        String functionName = getSignature().getName().getLocalName();
        
        Sequence seqA = args[0].convertTo(Type.DOUBLE);
        NumericValue valueA = (NumericValue)seqA.itemAt(0).convertTo(Type.DOUBLE);
        
        Sequence seqB = args[1].convertTo(Type.DOUBLE);
        NumericValue valueB = (NumericValue)seqB.itemAt(0).convertTo(Type.DOUBLE);
        
        // Do it
        calcValue=0.0;
        
        result=new DoubleValue(calcValue);
        
        
        if (context.getProfiler().isEnabled()){
            context.getProfiler().end(this, "", result);
        }
        
        return result;
    }
    
}

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
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Class containing math functions that accept no parameters.
 *
 * @author Dannes Wessels
 */
public class NoParamFunctions extends BasicFunction {
    
    //private static final Logger logger = LogManager.getLogger(NoParamFunctions.class);

    public static final String PI = "pi";

    public final static FunctionSignature FNS_PI = new FunctionSignature(
        new QName(PI, MathModule.NAMESPACE_URI, MathModule.PREFIX),
        "Returns the value of pi.",
        null,
        new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the value of pi")
    );

    public NoParamFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
	public int getDependencies() {
		return Dependency.NO_DEPENDENCY;
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
        final String functionName = getSignature().getName().getLocalPart();
        if(PI.equals(functionName)) {
            result=new DoubleValue(Math.PI);
            
        } else {
            throw new XPathException(this, "Function "+functionName+" not found.");
        }
        
        
        if (context.getProfiler().isEnabled()){
            context.getProfiler().end(this, "", result);
        }
        
        return result;
    }
    
}

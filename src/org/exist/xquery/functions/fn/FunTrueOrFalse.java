/* eXist Open Source Native XML Database
 * Copyright (C) 2000-09,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class FunTrueOrFalse extends BasicFunction {

	public final static FunctionSignature fnTrue =
			new FunctionSignature(
				new QName("true", Function.BUILTIN_FUNCTION_NS),
                "Always returns the boolean value true",
				null,
				new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true"));
	
	public final static FunctionSignature fnFalse =
		new FunctionSignature(
			new QName("false", Function.BUILTIN_FUNCTION_NS),
            "Always returns the boolean value false",
			null,
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "false"));
				
	public FunTrueOrFalse(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Function#getDependencies()
     */
    public int getDependencies() {
        return Dependency.NO_DEPENDENCY;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Function#getCardinality()
     */
    public int getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }
    
	public Sequence eval(Sequence args[], Sequence contextSequence) {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }
        
        Sequence result;
        if (isCalledAs("true"))
            {result = BooleanValue.TRUE;}
		else 
            {result = BooleanValue.FALSE;}        
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}        
        
        return result;          
	}
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunUpperOrLowerCase extends Function {

	public final static FunctionSignature fnUpperCase =
		new FunctionSignature(
			new QName("upper-case", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of $arg after translating every character to its upper-case correspondent as defined in the appropriate case mappings section in the Unicode standard. For versions of Unicode beginning with the 2.1.8 update, only locale-insensitive case mappings should be applied. Beginning with version 3.2.0 (and likely future versions) of Unicode, precise mappings are described in default case operations, which are full case mappings in the absence of tailoring for particular languages and environments. Every lower-case character that does not have an upper-case correspondent, as well as every upper-case character, is included in the returned value in its original form.",
			new SequenceType[] { new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The text to be converted to all upper-case characters") },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the resulting upper-case text"));
	
	public final static FunctionSignature fnLowerCase =
		new FunctionSignature(
			new QName("lower-case", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of $arg after translating every character to its lower-case correspondent as defined in the appropriate case mappings section in the Unicode standard. For versions of Unicode beginning with the 2.1.8 update, only locale-insensitive case mappings should be applied. Beginning with version 3.2.0 (and likely future versions) of Unicode, precise mappings are described in default case operations, which are full case mappings in the absence of tailoring for particular languages and environments. Every upper-case character that does not have a lower-case correspondent, as well as every lower-case character, is included in the returned value in its original form.",
			new SequenceType[] { new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The text to be converted to all lower-case characters") },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the resulting lower-case text"));

	public FunUpperOrLowerCase(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		if(contextItem != null)
			{contextSequence = contextItem.toSequence();}
        
        Sequence result;
		final Sequence seq = getArgument(0).eval(contextSequence);
		if (seq.isEmpty())
            {result = StringValue.EMPTY_STRING;}
        else {
    		final String value = seq.getStringValue();
    		if(isCalledAs("upper-case"))
                {result = new StringValue(value.toUpperCase());}
    		else
                {result = new StringValue(value.toLowerCase());}
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}        
        
        return result;          
	}

}

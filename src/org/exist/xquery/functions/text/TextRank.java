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
package org.exist.xquery.functions.text;

import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
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
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class TextRank extends BasicFunction {
	
	public final static FunctionSignature signature = new FunctionSignature(
		new QName("text-rank", TextModule.NAMESPACE_URI, TextModule.PREFIX),
		"This is just a skeleton for a possible ranking function. Don't use this.",
		new SequenceType[]{
			new FunctionParameterSequenceType("text", Type.NODE, Cardinality.ZERO_OR_ONE, "The text to rank")},
		new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the ranking of the text"));
	
	public TextRank(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }  
        
        Sequence result;
        
		// return 0.0 if the argument sequence is empty
		//TODO : return empty sequence ?
		if(args[0].isEmpty())
			{result = DoubleValue.ZERO;}
		else {
			final NodeValue val = (NodeValue)args[0].itemAt(0);
			// Ranking cannot be applied to constructed nodes
			if(val.getImplementationType() == NodeValue.IN_MEMORY_NODE)
				{throw new XPathException(this, getName() + " cannot be applied to in-memory nodes.");}
			final NodeProxy proxy = (NodeProxy)val;	// this is a persistent node, so casting is safe
	
			int freq = 0;
			Match nextMatch = proxy.getMatches();
			// we just count the number of distinct terms matched
			while(nextMatch != null) {
				freq += nextMatch.getFrequency();
				nextMatch = nextMatch.getNextMatch();
			}
			result = new DoubleValue(freq);
		}
		
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        
        return result;
		
	}
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * @author wolf
 */
public class MatchCount extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
    	new QName("match-count", TextModule.NAMESPACE_URI, TextModule.PREFIX),
    		"Counts the number of fulltext matches within the nodes and subnodes in $source.",
    		new SequenceType[]{
    			new FunctionParameterSequenceType("source", Type.NODE, Cardinality.ZERO_OR_ONE, "The node and subnodes to do the fulltext match on")},
    		new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the count"));
    
    /**
     * @param context
     */
    public MatchCount(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }  
        
        Sequence result;
        // return 0 if the argument sequence is empty
    	//TODO : return empty sequence ?
		if(args[0].isEmpty())
			{result = IntegerValue.ZERO;}
		else {
			int count = 0;
			for(final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
			    final Item next = i.nextItem();
			    if(Type.subTypeOf(next.getType(), Type.NODE)) {
			        final NodeValue nv = (NodeValue)next;
			        if(nv.getImplementationType() != NodeValue.PERSISTENT_NODE)
			        	{throw new XPathException(this, getName() + " cannot be applied to in-memory nodes.");}
			        final NodeProxy np = (NodeProxy)nv;
			        Match match = np.getMatches();
			        while (match != null) {
				        if (match.getNodeId().isDescendantOrSelfOf(np.getNodeId())) {
				        	count += match.getFrequency();
				        }
				        match = match.getNextMatch();
				   }
			    }
			}
			result = new IntegerValue(count);
		}

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        
        return result;
    }

}

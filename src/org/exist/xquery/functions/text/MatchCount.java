/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
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
    		"Counts the number of fulltext matches within the nodes and subnodes in $a.",
    		new SequenceType[]{
    			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)},
    		new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE));
    
    /**
     * @param context
     * @param signature
     */
    public MatchCount(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        // return 0.0 if the argument sequence is empty
		if(args[0].isEmpty())
			return IntegerValue.ZERO;
		int count = 0;
		for(SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
		    Item next = i.nextItem();
		    if(Type.subTypeOf(next.getType(), Type.NODE)) {
		        NodeValue nv = (NodeValue)next;
		        if(nv.getImplementationType() != NodeValue.PERSISTENT_NODE)
		            throw new XPathException(getName() + " cannot be applied to XQuery-constructed nodes.");
		        NodeProxy np = (NodeProxy)nv;
		        for(Match nextMatch = np.getMatches(); nextMatch != null; ) {
		        	count += nextMatch.getFrequency();
		            nextMatch = nextMatch.getNextMatch();
		        }
		    }
		}
		return new IntegerValue(count);
    }

}

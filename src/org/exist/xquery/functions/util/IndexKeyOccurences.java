/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006 The eXist Team
 *
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
 *  $Id: QNameIndexLookup.java 3063 2006-04-05 20:49:44Z brihaye $
 */
package org.exist.xquery.functions.util;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.Indexable;
import org.exist.util.ValueOccurrences;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class IndexKeyOccurences extends BasicFunction {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("index-key-occurences", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Return the number of occurences for an indexed value. " +
            "The first argument specifies the nodes whose content is indexed. " +
            "The second argument specifies the value. ",
			new SequenceType[] {
					new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.ATOMIC, Cardinality.EXACTLY_ONE) },
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE));

	public IndexKeyOccurences(XQueryContext context) {
		super(context, signature);
	}
    
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
         }  
        
    	Sequence result;
    	if (args[0].isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
    	else {
	        NodeSet nodes = args[0].toNodeSet();
	        DocumentSet docs = nodes.getDocumentSet();	        
	        ValueOccurrences occur[] = context.getBroker().getValueIndex()
	                .scanIndexKeys(docs, nodes, (Indexable) args[1]);
	        if (occur.length == 0)
	        	result= Sequence.EMPTY_SEQUENCE;
	        else
	        	result = new IntegerValue(occur[0].getOccurrences());
    	}
    	
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 

        return result;
    }
}

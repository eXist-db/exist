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

import java.util.HashMap;
import java.util.Map;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.storage.Indexable;
import org.exist.util.Occurrences;
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
public class IndexKeyDocuments extends BasicFunction {

	public final static FunctionSignature[] signatures = {
		new FunctionSignature(
				new QName("index-key-documents", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Return the number of documents for an indexed value. " +
	            "The first argument specifies the nodes whose content is indexed. " +
	            "The second argument specifies the value. ",
				new SequenceType[] {
						new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
						new SequenceType(Type.ATOMIC, Cardinality.EXACTLY_ONE) },
				new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE)),
		new FunctionSignature(
				new QName("index-key-documents", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Return the number of documents for an indexed value. " +
	            "The first argument specifies the nodes whose content is indexed. " +
	            "The second argument specifies the value. " +
	            "The third argument specifies the index in which the search is made",
				new SequenceType[] {
						new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
						new SequenceType(Type.ATOMIC, Cardinality.EXACTLY_ONE), 
						new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
				new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE))				
	};

	public IndexKeyDocuments(XQueryContext context, FunctionSignature signature) {
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
	        if (this.getArgumentCount() == 3) {
	        	IndexWorker indexWorker = context.getBroker().getIndexController().getWorkerByIndexName(args[2].itemAt(0).getStringValue());
	        	//Alternate design
	        	//IndexWorker indexWorker = context.getBroker().getBrokerPool().getIndexManager().getIndexByName(args[2].itemAt(0).getStringValue()).getWorker();	        	
	        	if (indexWorker == null)
	        		throw new XPathException("Unknown index: " + args[2].itemAt(0).getStringValue());
	        	Map hints = new HashMap();
	        	if (indexWorker instanceof OrderedValuesIndex)
	        		hints.put(OrderedValuesIndex.START_VALUE, args[1]);
	        	else
	        		LOG.info(indexWorker + " isn't an instance of org.exist.indexing.OrderedIndexWorker. " + args[1] + " ignored." );
	        	Occurrences[] occur = indexWorker.scanIndex(context, docs, nodes, hints);
		        if (occur.length == 0)
		        	result= Sequence.EMPTY_SEQUENCE;
		        else
		        	result = new IntegerValue(occur[0].getDocuments());
	        } else {
		        ValueOccurrences occur[] = context.getBroker().getValueIndex()
                .scanIndexKeys(docs, nodes, (Indexable) args[1]);
		        if (occur.length == 0)
		        	result= Sequence.EMPTY_SEQUENCE;
		        else
		        	result = new IntegerValue(occur[0].getDocuments());        	
	        }
    	}
    	
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 

        return result;
    }
}

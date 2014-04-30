/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2009 The eXist Team
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

import org.apache.log4j.Logger;
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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class IndexKeyOccurrences extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(IndexKeyOccurrences.class);
	protected static final FunctionParameterSequenceType nodeParam = new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes whose content is indexed");
	protected static final FunctionParameterSequenceType valueParam = new FunctionParameterSequenceType("value", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The indexed value to search for");
	protected static final FunctionParameterSequenceType indexParam = new FunctionParameterSequenceType("index", Type.STRING, Cardinality.EXACTLY_ONE, "The index in which the search is made");
	protected static final FunctionReturnSequenceType result = new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the number of occurrences for the indexed value");

	public final static FunctionSignature signatures[] = { 
		new FunctionSignature(
				new QName("index-key-occurrences", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Return the number of occurrences for an indexed value.",
				new SequenceType[] { nodeParam, valueParam },
				result),
		new FunctionSignature(
				new QName("index-key-occurrences", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Return the number of occurrences for an indexed value.",
				new SequenceType[] { nodeParam, valueParam, indexParam },
				result)			
	};

	public IndexKeyOccurrences(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
    
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
         }  
        
    	Sequence result;
    	if (args[0].isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
    	else {
	        final NodeSet nodes = args[0].toNodeSet();
	        final DocumentSet docs = nodes.getDocumentSet();
	        
	        if (this.getArgumentCount() == 3){
	        	final IndexWorker indexWorker = context.getBroker().getIndexController().getWorkerByIndexName(args[2].itemAt(0).getStringValue());
	        	//Alternate design
	        	//IndexWorker indexWorker = context.getBroker().getBrokerPool().getIndexManager().getIndexByName(args[2].itemAt(0).getStringValue()).getWorker();
	        	if (indexWorker == null)
	        		{throw new XPathException(this, "Unknown index: " + args[2].itemAt(0).getStringValue());}
	        	final Map<String, Object> hints = new HashMap<String, Object>();
	        	if (indexWorker instanceof OrderedValuesIndex)
	        		{hints.put(OrderedValuesIndex.START_VALUE, args[1]);}
	        	else
	        		{logger.warn(indexWorker.getClass().getName() + " isn't an instance of org.exist.indexing.OrderedIndexWorker. Start value '" + args[1] + "' ignored." );}
	        	final Occurrences[] occur = indexWorker.scanIndex(context, docs, nodes, hints);
		        if (occur.length == 0)
		        	{result= Sequence.EMPTY_SEQUENCE;}
		        else
		        	{result = new IntegerValue(occur[0].getOccurrences());}
	        } else {
	        	ValueOccurrences occur[] = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, (Indexable) (args[1].itemAt(0)));
		        if (occur.length == 0)
		        	{occur = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, null, (Indexable) (args[1].itemAt(0)));}
		        if (occur.length == 0)
                    {result = Sequence.EMPTY_SEQUENCE;}
                else
                    {result = new IntegerValue(occur[0].getOccurrences());}
	        }
    	}
    	
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 

        return result;
    }
}

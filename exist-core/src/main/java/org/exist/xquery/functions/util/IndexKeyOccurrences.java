/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
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
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
public class IndexKeyOccurrences extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(IndexKeyOccurrences.class);
	protected static final FunctionParameterSequenceType nodeParam = new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes whose content is indexed");
	protected static final FunctionParameterSequenceType valueParam = new FunctionParameterSequenceType("value", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The indexed value to search for");
	protected static final FunctionParameterSequenceType indexParam = new FunctionParameterSequenceType("index", Type.STRING, Cardinality.EXACTLY_ONE, "The index in which the search is made");
	protected static final FunctionReturnSequenceType result = new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the number of occurrences for the indexed value");

	public final static FunctionSignature[] signatures = {
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
	        	final Map<String, Object> hints = new HashMap<>();
	        	if (indexWorker instanceof OrderedValuesIndex)
	        		{hints.put(OrderedValuesIndex.START_VALUE, args[1]);}
	        	else
	        		{
						logger.warn("{} isn't an instance of org.exist.indexing.OrderedIndexWorker. Start value '{}' ignored.", indexWorker.getClass().getName(), args[1]);}
	        	final Occurrences[] occur = indexWorker.scanIndex(context, docs, nodes, hints);
		        if (occur.length == 0)
		        	{result= Sequence.EMPTY_SEQUENCE;}
		        else
		        	{result = new IntegerValue(this, occur[0].getOccurrences());}
	        } else {
	        	ValueOccurrences[] occur = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, (Indexable) (args[1].itemAt(0)));
		        if (occur.length == 0)
		        	{occur = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, null, (Indexable) (args[1].itemAt(0)));}
		        if (occur.length == 0)
                    {result = Sequence.EMPTY_SEQUENCE;}
                else
                    {result = new IntegerValue(this, occur[0].getOccurrences());}
	        }
    	}
    	
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 

        return result;
    }
}

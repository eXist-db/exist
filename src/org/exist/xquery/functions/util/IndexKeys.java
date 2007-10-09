/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 * 
 */
public class IndexKeys extends BasicFunction {

    public final static FunctionSignature[] signatures = {
    	new FunctionSignature(
                new QName("index-keys", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Can be used to query existing range indexes defined on a set of nodes. " +
                "All index keys defined for the given node set are reported to a callback function. " +
                "The node set is specified in the first argument. The second argument specifies a start " +
                "value. Only index keys of the same type but being greater than $b will be reported for non-string" +
                "types. For string types, only keys starting with the given prefix are reported. " +
                "The third arguments is a function reference as created by the util:function function. " +
                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                "containing three int values: a) the overall frequency of the key within the node set, " +
                "b) the number of distinct documents in the node set the key occurs in, " +
                "c) the current position of the key in the whole list of keys returned. " +
                "The fourth argument is the maximum number of returned keys", 
                new SequenceType[] {
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                    new SequenceType(Type.ATOMIC, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.INT, Cardinality.EXACTLY_ONE)
                 },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)),
    	new FunctionSignature(
                new QName("index-keys", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Can be used to query existing range indexes defined on a set of nodes. " +
                "All index keys defined for the given node set are reported to a callback function. " +
                "The node set is specified in the first argument. The second argument specifies a start " +
                "value. Only index keys of the same type but being greater than $b will be reported for non-string" +
                "types. For string types, only keys starting with the given prefix are reported. " +
                "The third arguments is a function reference as created by the util:function function. " +
                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                "containing three int values: a) the overall frequency of the key within the node set, " +
                "b) the number of distinct documents in the node set the key occurs in, " +
                "c) the current position of the key in the whole list of keys returned. " +
                "The fourth argument is the maximum number of returned keys" +
                "The fifth argument specifies the index in which the search is made", 
                new SequenceType[] {
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                    new SequenceType(Type.ATOMIC, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.INT, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
                },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE))      
    };

    /**
     * @param context
     */
    public IndexKeys(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
     *      org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        NodeSet nodes = args[0].toNodeSet();
        DocumentSet docs = nodes.getDocumentSet();
        FunctionReference ref = (FunctionReference) args[2].itemAt(0);
        int max = ((IntegerValue) args[3].itemAt(0)).getInt();
        FunctionCall call = ref.getFunctionCall();
        Sequence result = new ValueSequence();
        if (this.getArgumentCount() == 5) {
        	IndexWorker indexWorker = context.getBroker().getIndexController().getWorkerByIndexName(args[4].itemAt(0).getStringValue());
        	//Alternate design
        	//IndexWorker indexWorker = context.getBroker().getBrokerPool().getIndexManager().getIndexByName(args[4].itemAt(0).getStringValue()).getWorker();
        	if (indexWorker == null)
        		throw new XPathException("Unknown index: " + args[4].itemAt(0).getStringValue());
        	Map hints = new HashMap();
        	hints.put(IndexWorker.VALUE_COUNT, new IntegerValue(max));
        	if (indexWorker instanceof OrderedValuesIndex)
        		hints.put(OrderedValuesIndex.START_VALUE, args[1]);
        	else
        		LOG.info(indexWorker + " isn't an instance of org.exist.indexing.OrderedIndexWorker. " + args[1] + " ignored." );
        	Occurrences[] occur = indexWorker.scanIndex(context, docs, nodes, hints);        	
        	//TODO : add an extra argument to pass the END_VALUE ?
	        int len = (occur.length > max ? max : occur.length);
	        Sequence params[] = new Sequence[2];
	        ValueSequence data = new ValueSequence();
	        for (int j = 0; j < len; j++) {
	            params[0] = new StringValue(occur[j].getTerm().toString());
	            data.add(new IntegerValue(occur[j].getOccurrences(),
	                    Type.UNSIGNED_INT));
	            data.add(new IntegerValue(occur[j].getDocuments(),
	                    Type.UNSIGNED_INT));
	            data.add(new IntegerValue(j + 1, Type.UNSIGNED_INT));
	            params[1] = data;
	
	            result.addAll(call.evalFunction(contextSequence, null, params));
	            data.clear();
	        }
        } else {
	        ValueOccurrences occur[] = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, (Indexable) args[1]);
		    int len = (occur.length > max ? max : occur.length);
		    Sequence params[] = new Sequence[2];
		    ValueSequence data = new ValueSequence();
		    for (int j = 0; j < len; j++) {
		        params[0] = occur[j].getValue();
		        data.add(new IntegerValue(occur[j].getOccurrences(),
		                Type.UNSIGNED_INT));
		        data.add(new IntegerValue(occur[j].getDocuments(),
		                Type.UNSIGNED_INT));
		        data.add(new IntegerValue(j + 1, Type.UNSIGNED_INT));
		        params[1] = data;
		
		        result.addAll(call.evalFunction(contextSequence, null, params));
		        data.clear();
		    }
        }
        if (LOG.isDebugEnabled())
        	LOG.debug("Returning: " + result.getItemCount());
        return result;
    }

}

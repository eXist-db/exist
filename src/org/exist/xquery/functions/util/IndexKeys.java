/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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

import org.apache.log4j.Logger;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.Indexable;
import org.exist.util.Occurrences;
import org.exist.util.ValueOccurrences;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.*;

/**
 * @author wolf
 * 
 */
public class IndexKeys extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(IndexKeys.class);

    public final static FunctionSignature[] signatures = {
    	new FunctionSignature(
                new QName("index-keys", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Can be used to query existing range indexes defined on a set of nodes. " +
                "All index keys defined for the given node set are reported to a callback function. " +
                "The function will check for indexes defined on path as well as indexes defined by QName. ",
                new SequenceType[] {
                    new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_MORE, "The node set"),
                    new FunctionParameterSequenceType("start-value", Type.ATOMIC, Cardinality.EXACTLY_ONE, "Only index keys of the same type but being greater than $start-value will be reported for non-string types. For string types, only keys starting with the given prefix are reported."),
                    new FunctionParameterSequenceType("function-reference", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function reference as created by the util:function function. " +
                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                "containing three int values: a) the overall frequency of the key within the node set, " +
                "b) the number of distinct documents in the node set the key occurs in, " +
                "c) the current position of the key in the whole list of keys returned."),
                    new FunctionParameterSequenceType("max-number-returned", Type.INT, Cardinality.EXACTLY_ONE, "The maximum number of returned keys")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference")),
    	new FunctionSignature(
                new QName("index-keys", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Can be used to query existing range indexes defined on a set of nodes. " +
                "All index keys defined for the given node set are reported to a callback function. " +
                "The function will check for indexes defined on path as well as indexes defined by QName. ",
                new SequenceType[] {
                    new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_MORE, "The node set"),
                    new FunctionParameterSequenceType("start-value", Type.ATOMIC, Cardinality.EXACTLY_ONE, "Only index keys of the same type but being greater than $start-value will be reported for non-string types. For string types, only keys starting with the given prefix are reported."),
                    new FunctionParameterSequenceType("function-reference", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function reference as created by the util:function function. " +
                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                "containing three int values: a) the overall frequency of the key within the node set, " +
                "b) the number of distinct documents in the node set the key occurs in, " +
                "c) the current position of the key in the whole list of keys returned."),
                    new FunctionParameterSequenceType("max-number-returned", Type.INT, Cardinality.EXACTLY_ONE, "The maximum number of returned keys"),
                    new FunctionParameterSequenceType("index", Type.STRING, Cardinality.EXACTLY_ONE, "The index in which the search is made")
                },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference"))      
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
        		throw new XPathException(this, "Unknown index: " + args[4].itemAt(0).getStringValue());
        	Map hints = new HashMap();
        	hints.put(IndexWorker.VALUE_COUNT, new IntegerValue(max));
        	if (indexWorker instanceof OrderedValuesIndex)
        		hints.put(OrderedValuesIndex.START_VALUE, args[1]);
        	else
        		logger.warn(indexWorker.getClass().getName() + " isn't an instance of org.exist.indexing.OrderedIndexWorker. Start value '" + args[1] + "' ignored." );
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
            int idxType = nodes.getIndexType();
            Indexable indexable = (Indexable) args[1].itemAt(0);
            ValueOccurrences occur[] = null;
            // First check for indexes defined on qname
            QName[] qnames = getDefinedIndexes(context.getBroker(), docs);
            if (qnames != null && qnames.length > 0)
                occur = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, qnames, indexable);
            // Also check if there's an index defined by path
            ValueOccurrences occur2[] = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, indexable);
            // Merge the two results
            if (occur == null || occur.length == 0)
                occur = occur2;
            else {
                ValueOccurrences t[] = new ValueOccurrences[occur.length + occur2.length];
                System.arraycopy(occur, 0, t, 0, occur.length);
                System.arraycopy(occur2, 0, t, occur.length, occur2.length);
                occur = t;
            }

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
    	logger.debug("Returning: " + result.getItemCount());
        return result;
    }

    /**
     * Check index configurations for all collection in the given DocumentSet and return
     * a list of QNames, which have indexes defined on them.
     *
     * @param broker
     * @param docs
     * @return
     */
    private QName[] getDefinedIndexes(DBBroker broker, DocumentSet docs) {
        Set indexes = new HashSet();
        for (Iterator i = docs.getCollectionIterator(); i.hasNext(); ) {
            final org.exist.collections.Collection collection = (org.exist.collections.Collection) i.next();
            final IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                final List qnames = idxConf.getIndexedQNames();
                for (int j = 0; j < qnames.size(); j++) {
                    final QName qName = (QName) qnames.get(j);
                    indexes.add(qName);
                }
            }
        }
        QName qnames[] = new QName[indexes.size()];
        return (QName[]) indexes.toArray(qnames);
    }
}

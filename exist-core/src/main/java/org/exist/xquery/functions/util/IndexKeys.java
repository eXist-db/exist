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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.QNamedKeysIndex;
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
	
	protected static final Logger logger = LogManager.getLogger(IndexKeys.class);

    public final static FunctionSignature[] signatures = {
    	new FunctionSignature(
                new QName("index-keys", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Can be used to query existing range indexes defined on a set of nodes. " +
                "All index keys defined for the given node set are reported to a callback function. " +
                "The function will check for indexes defined on path as well as indexes defined by QName. ",
                new SequenceType[] {
                    new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_MORE, "The node set"),
                    new FunctionParameterSequenceType("start-value", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "Only index keys of the same type but being greater than $start-value will be reported for non-string types. For string types, only keys starting with the given prefix are reported."),
                    new FunctionParameterSequenceType("function-reference", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function reference as created by the util:function function. " +
                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                "containing three int values: a) the overall frequency of the key within the node set, " +
                "b) the number of distinct documents in the node set the key occurs in, " +
                "c) the current position of the key in the whole list of keys returned."),
                    new FunctionParameterSequenceType("max-number-returned", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The maximum number of returned keys")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference")),
    	new FunctionSignature(
                new QName("index-keys", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Can be used to query existing range indexes defined on a set of nodes. " +
                "All index keys defined for the given node set are reported to a callback function. " +
                "The function will check for indexes defined on path as well as indexes defined by QName. ",
                new SequenceType[] {
                    new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_MORE, "The node set"),
                    new FunctionParameterSequenceType("start-value", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "Only index keys of the same type but being greater than $start-value will be reported for non-string types. For string types, only keys starting with the given prefix are reported."),
                    new FunctionParameterSequenceType("function-reference", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function reference as created by the util:function function. " +
                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                "containing three int values: a) the overall frequency of the key within the node set, " +
                "b) the number of distinct documents in the node set the key occurs in, " +
                "c) the current position of the key in the whole list of keys returned."),
                    new FunctionParameterSequenceType("max-number-returned", Type.INTEGER, Cardinality.ZERO_OR_ONE , "The maximum number of returned keys"),
                    new FunctionParameterSequenceType("index", Type.STRING, Cardinality.EXACTLY_ONE, "The index in which the search is made")
                },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference")),
        new FunctionSignature(
                new QName("index-keys-by-qname", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                "Can be used to query existing range indexes defined on a set of nodes. " +
                "All index keys defined for the given node set are reported to a callback function. " +
                "The function will check for indexes defined on path as well as indexes defined by QName. ",
                new SequenceType[] {
                    new FunctionParameterSequenceType("qname", Type.QNAME, Cardinality.ZERO_OR_MORE, "The node set"),
                    new FunctionParameterSequenceType("start-value", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "Only index keys of the same type but being greater than $start-value will be reported for non-string types. For string types, only keys starting with the given prefix are reported."),
                    new FunctionParameterSequenceType("function-reference", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function reference as created by the util:function function. " +
                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                "containing three int values: a) the overall frequency of the key within the node set, " +
                "b) the number of distinct documents in the node set the key occurs in, " +
                "c) the current position of the key in the whole list of keys returned."),
                    new FunctionParameterSequenceType("max-number-returned", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The maximum number of returned keys"),
                    new FunctionParameterSequenceType("index", Type.STRING, Cardinality.EXACTLY_ONE, "The index in which the search is made")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference")),
    };

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
            {return Sequence.EMPTY_SEQUENCE;}
        NodeSet nodes = null;
        DocumentSet docs = null;
        Sequence qnames = null;
        if (isCalledAs("index-keys-by-qname")) {
            qnames = args[0];
            docs = contextSequence == null ? context.getStaticallyKnownDocuments() : contextSequence.getDocumentSet();
        } else {
            nodes = args[0].toNodeSet();
            docs = nodes.getDocumentSet();
        }
        final Sequence result = new ValueSequence();
        try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
            int max = -1;
            if (args[3].hasOne()) {
                max = ((IntegerValue) args[3].itemAt(0)).getInt();
            }
            // if we have 5 arguments, query the user-specified index
            if (this.getArgumentCount() == 5) {
                final IndexWorker indexWorker = context.getBroker().getIndexController().getWorkerByIndexName(args[4].itemAt(0).getStringValue());
                //Alternate design
                //IndexWorker indexWorker = context.getBroker().getBrokerPool().getIndexManager().getIndexByName(args[4].itemAt(0).getStringValue()).getWorker();
                if (indexWorker == null) {
                    throw new XPathException(this, "Unknown index: " + args[4].itemAt(0).getStringValue());
                }
                final Map<String, Object> hints = new HashMap<>();
                if (max != -1) {
                    hints.put(IndexWorker.VALUE_COUNT, new IntegerValue(this, max));
                }
                if (indexWorker instanceof OrderedValuesIndex) {
                    hints.put(OrderedValuesIndex.START_VALUE, args[1].getStringValue());
                } else {
                    logger.warn("{} isn't an instance of org.exist.indexing.OrderedValuesIndex. Start value '{}' ignored.", indexWorker.getClass().getName(), args[1]);
                }
                if (qnames != null) {
                    final List<QName> qnameList = new ArrayList<>(qnames.getItemCount());
                    for (final SequenceIterator i = qnames.iterate(); i.hasNext(); ) {
                        final QNameValue qv = (QNameValue) i.nextItem();
                        qnameList.add(qv.getQName());
                    }
                    hints.put(QNamedKeysIndex.QNAMES_KEY, qnameList);
                }
                final Occurrences[] occur = indexWorker.scanIndex(context, docs, nodes, hints);
                //TODO : add an extra argument to pass the END_VALUE ?
                final int len = (max != -1 && occur.length > max ? max : occur.length);
                final Sequence[] params = new Sequence[2];
                ValueSequence data = new ValueSequence();
                for (int j = 0; j < len; j++) {
                    params[0] = new StringValue(this, occur[j].getTerm().toString());
                    data.add(new IntegerValue(this, occur[j].getOccurrences(),
                            Type.UNSIGNED_INT));
                    data.add(new IntegerValue(this, occur[j].getDocuments(),
                            Type.UNSIGNED_INT));
                    data.add(new IntegerValue(this, j + 1, Type.UNSIGNED_INT));
                    params[1] = data;

                    result.addAll(ref.evalFunction(Sequence.EMPTY_SEQUENCE, null, params));
                    data.clear();
                }
                // no index specified: use the range index
            } else {
                final Indexable indexable = (Indexable) args[1].itemAt(0);
                ValueOccurrences[] occur = null;
                // First check for indexes defined on qname
                final QName[] allQNames = getDefinedIndexes(context.getBroker(), docs);
                if (allQNames.length > 0) {
                    occur = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, allQNames, indexable);
                }
                // Also check if there's an index defined by path
                ValueOccurrences[] occur2 = context.getBroker().getValueIndex().scanIndexKeys(docs, nodes, indexable);
                // Merge the two results
                if (occur == null || occur.length == 0) {
                    occur = occur2;
                } else {
                    ValueOccurrences[] t = new ValueOccurrences[occur.length + occur2.length];
                    System.arraycopy(occur, 0, t, 0, occur.length);
                    System.arraycopy(occur2, 0, t, occur.length, occur2.length);
                    occur = t;
                }

                final int len = (max != -1 && occur.length > max ? max : occur.length);
                final Sequence[] params = new Sequence[2];
                ValueSequence data = new ValueSequence();
                for (int j = 0; j < len; j++) {
                    params[0] = occur[j].getValue();
                    data.add(new IntegerValue(this, occur[j].getOccurrences(),
                            Type.UNSIGNED_INT));
                    data.add(new IntegerValue(this, occur[j].getDocuments(),
                            Type.UNSIGNED_INT));
                    data.add(new IntegerValue(this, j + 1, Type.UNSIGNED_INT));
                    params[1] = data;

                    result.addAll(ref.evalFunction(Sequence.EMPTY_SEQUENCE, null, params));
                    data.clear();
                }
            }
        }
        logger.debug("Returning: {}", result.getItemCount());
        return result;
    }

    
    @Override
	public int getDependencies() {
    	if (isCalledAs("index-keys-by-qname")) {
            return Dependency.CONTEXT_SET;
        } else {
            return getArgument(0).getDependencies();
        }
	}

	/**
     * Check index configurations for all collection in the given DocumentSet and return
     * a list of QNames, which have indexes defined on them.
     *
     * @param broker
     * @param docs
     */
    private QName[] getDefinedIndexes(DBBroker broker, DocumentSet docs) {
        final Set<QName> indexes = new HashSet<>();
        for (final Iterator<org.exist.collections.Collection> i = docs.getCollectionIterator(); i.hasNext(); ) {
            final org.exist.collections.Collection collection = i.next();
            final IndexSpec idxConf = collection.getIndexConfiguration(broker);
            if (idxConf != null) {
                final List<QName> qnames = idxConf.getIndexedQNames();
                for (QName qname : qnames) {
                    final QName qName = (QName) qname;
                    indexes.add(qName);
                }
            }
        }
        final QName[] qnames = new QName[indexes.size()];
        return indexes.toArray(qnames);
    }
}

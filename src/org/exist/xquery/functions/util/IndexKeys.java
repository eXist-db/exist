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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.Indexable;
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
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 * 
 */
public class IndexKeys extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
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
                "c) the current position of the key in the whole list of keys returned.", 
                new SequenceType[] {
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                    new SequenceType(Type.ATOMIC, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.FUNCTION_REFERENCE,
                            Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.INT, Cardinality.EXACTLY_ONE) },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));

    /**
     * @param context
     */
    public IndexKeys(XQueryContext context) {
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
        ValueOccurrences occur[] = context.getBroker().getValueIndex()
                .scanIndexKeys(docs, nodes, (Indexable) args[1]);
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
        if (LOG.isDebugEnabled())
        	LOG.debug("Returning: " + result.getItemCount());
        return result;
    }

}

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
package org.exist.xquery.functions.text;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.FulltextIndexSpec;
import org.exist.util.Occurrences;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.*;

/**
 * @author wolf
 */
public class IndexTerms extends BasicFunction {

    public final static FunctionSignature signatures[] = new FunctionSignature[] {
        new FunctionSignature(
            new QName("index-terms", TextModule.NAMESPACE_URI, TextModule.PREFIX),
            "This function can be used to collect some information on the distribution " +
            "of index terms within a set of nodes. The set of nodes is specified in the first " +
            "argument $nodes. The function returns term frequencies for all terms in the index found " +
            "in descendants of the nodes in $nodes. The second argument $start specifies " +
            "a start string. Only terms starting with the specified character sequence are returned. " +
            "If $nodes is the empty sequence, all terms in the index will be selected. " +
            "$function is a function reference, which points to a callback function that will be called " +
            "for every term occurrence. $returnMax defines the maximum number of terms that should be " +
            "reported. The function reference for $function can be created with the util:function " +
            "function. It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
            "1) the current term as found in the index as xs:string, 2) a sequence containing four int " +
            "values: a) the overall frequency of the term within the node set, b) the number of distinct " +
            "documents in the node set the term occurs in, c) the current position of the term in the whole " +
            "list of terms returned, d) the rank of the current term in the whole list of terms returned.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                        "The set of nodes in which the returned tokens occur"),
                    new FunctionParameterSequenceType("start", Type.STRING, Cardinality.ZERO_OR_ONE,
                        "The optional start string"),
                    new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE,
                        "The callback function reference"),
                    new FunctionParameterSequenceType("returnMax", Type.INT, Cardinality.EXACTLY_ONE,
                        "The maximum number of terms to report")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results from the evaluation of the function reference")),
        new FunctionSignature(
            new QName("index-terms", TextModule.NAMESPACE_URI, TextModule.PREFIX),
            "This version of the index-terms function is to be used with indexes that were " +
            "defined on a specific element or attribute QName. The second argument " +
            "lists the QNames or elements or attributes for which occurrences should be" +
            "returned. Otherwise, the function behaves like the 4-argument version.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                        "The set of nodes in which the returned tokens occur"),
                    new FunctionParameterSequenceType("qnames", Type.QNAME, Cardinality.ONE_OR_MORE,
                        "One or more element or attribute names for which index terms are returned"),
                    new FunctionParameterSequenceType("start", Type.STRING, Cardinality.ZERO_OR_ONE,
                        "The optional start string"),
                    new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE,
                        "The callback function reference"),
                    new FunctionParameterSequenceType("returnMax", Type.INT, Cardinality.EXACTLY_ONE,
                        "The maximum number of terms to report")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results from the evaluation of the function reference"))
    };
    
    public IndexTerms(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
        throws XPathException {
        int arg = 0;
        if (args[arg].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        NodeSet nodes = args[arg++].toNodeSet();
        DocumentSet docs = nodes.getDocumentSet();
        QName[] qnames = null;
        if (args.length == 5) {
            qnames = new QName[args[arg].getItemCount()];
            int q = 0;
            for (SequenceIterator i = args[arg].iterate(); i.hasNext(); q++) {
                QNameValue qnv = (QNameValue) i.nextItem();
                qnames[q] = qnv.getQName();
            }
            ++arg;
        } else
            qnames = getDefinedIndexes(context.getBroker(), docs);
        String start = null;
        if (!args[arg].isEmpty())
            start = args[arg].getStringValue();
        FunctionReference ref = (FunctionReference) args[++arg].itemAt(0);
        int max = ((IntegerValue) args[++arg].itemAt(0)).getInt();
        FunctionCall call = ref.getFunctionCall();
        Sequence result = new ValueSequence();
        try {
            Occurrences occur[] = context.getBroker().getTextEngine().scanIndexTerms(docs, nodes, qnames, start, null);
            if (args.length == 4) {
                Occurrences occur2[] = context.getBroker().getTextEngine().scanIndexTerms(docs, nodes, start, null);
                if (occur == null || occur.length == 0)
                    occur = occur2;
                else {
                    Occurrences t[] = new Occurrences[occur.length + occur2.length];
                    System.arraycopy(occur, 0, t, 0, occur.length);
                    System.arraycopy(occur2, 0, t, occur.length, occur2.length);
                    occur = t;
                }
            }
            int len = (occur.length > max ? max : occur.length);
            Sequence params[] = new Sequence[2];
            ValueSequence data = new ValueSequence();

            Vector list = new Vector(len);
            for (int j = 0; j < len; j++) {
                if (!list.contains(new Integer(occur[j].getOccurrences()))) {
                    list.add(new Integer(occur[j].getOccurrences()));
                }
            }
            Collections.sort(list);
            Collections.reverse(list);
            HashMap map = new HashMap(list.size() * 2);
            for (int j = 0; j < list.size(); j++) {
                map.put(list.get(j), new Integer(j + 1));
            }

            for (int j = 0; j < len; j++) {
                params[0] = new StringValue(occur[j].getTerm().toString());
                data.add(new IntegerValue(occur[j].getOccurrences(), Type.UNSIGNED_INT));
                data.add(new IntegerValue(occur[j].getDocuments(), Type.UNSIGNED_INT));
                data.add(new IntegerValue(j + 1, Type.UNSIGNED_INT));
                data.add(new IntegerValue(((Integer) map.get(new Integer(occur[j].getOccurrences()))).intValue(), Type.UNSIGNED_INT));

                params[1] = data;

                result.addAll(call.evalFunction(contextSequence, null, params));
                data.clear();
            }
            if (LOG.isDebugEnabled())
                LOG.debug("Returning: " + result.getItemCount());
            return result;
        } catch (PermissionDeniedException e) {
            throw new XPathException(this, e.getMessage(), e);
        }
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
                FulltextIndexSpec fIdxConf = idxConf.getFulltextIndexSpec();
                final List qnames = fIdxConf.getIndexedQNames();
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

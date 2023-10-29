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
package org.exist.xquery.modules.lucene;

import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentSet;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.OrderedValuesIndex;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.util.Occurrences;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.HashMap;
import java.util.Map;

public class LuceneIndexKeys extends BasicFunction {

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("index-keys-for-field", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Similar to the util:index-keys functions, but returns index entries for a field " +
                            "associated with a lucene index.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the field"),
                            new FunctionParameterSequenceType("start-value", Type.STRING, Cardinality.ZERO_OR_ONE, "Only keys starting with the given prefix are reported."),
                            new FunctionParameterSequenceType("function-reference", Type.FUNCTION, Cardinality.EXACTLY_ONE, "A function reference. " +
                                    "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                                    "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                                    "containing three int values: a) the overall frequency of the key within the node set, " +
                                    "b) the number of distinct documents in the node set the key occurs in, " +
                                    "c) the current position of the key in the whole list of keys returned."),
                            new FunctionParameterSequenceType("max-number-returned", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The maximum number of keys to return")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference"))
    };

    public LuceneIndexKeys(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final String fieldName = args[0].getStringValue();
        final DocumentSet docs = contextSequence == null ? context.getStaticallyKnownDocuments() : contextSequence.getDocumentSet();
        final Map<String, Object> hints = new HashMap<>();
        if (!args[1].isEmpty()) {
            hints.put(OrderedValuesIndex.START_VALUE, args[1].getStringValue());
        }
        IntegerValue max = null;
        if (args[3].hasOne()) {
            max = ((IntegerValue) args[3].itemAt(0));
        }
        if (max != null && max.getInt() > -1) {
            hints.put(IndexWorker.VALUE_COUNT, max);
        }
        final Sequence result = new ValueSequence();
        try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
            final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            final Occurrences[] occur = index.scanIndexByField(fieldName, docs, hints);
            final Sequence params[] = new Sequence[2];
            final ValueSequence data = new ValueSequence();
            for (int j = 0; j < occur.length; j++) {
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
        }
        return result;
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }
}

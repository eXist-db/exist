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
package org.exist.xquery.modules.range;

import org.exist.dom.QName;
import org.exist.indexing.range.RangeIndexWorker;
import org.exist.util.Occurrences;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class IndexKeys extends BasicFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("index-keys-for-field", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            "Retrieve all index keys contained in a range index which has been defined with a field name. Similar to" +
            "util:index-keys, but works with fields.",
            new SequenceType[] {
                new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE, "The field to use"),
                new FunctionParameterSequenceType("function-reference", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function reference as created by the util:function function. " +
                        "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                        "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                        "containing three int values: a) the overall frequency of the key within the node set, " +
                        "b) the number of distinct documents in the node set the key occurs in, " +
                        "c) the current position of the key in the whole list of keys returned."),
                new FunctionParameterSequenceType("max-number-returned", Type.INTEGER, Cardinality.ZERO_OR_ONE , "The maximum number of returned keys")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference")),
        new FunctionSignature(
                new QName("index-keys-for-field", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
                "Retrieve all index keys contained in a range index which has been defined with a field name. Similar to" +
                        "util:index-keys, but works with fields.",
                new SequenceType[] {
                        new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE, "The field to use"),
                        new FunctionParameterSequenceType("start-value", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "Only index keys of the same type but being greater than $start-value will be reported for non-string types. For string types, only keys starting with the given prefix are reported."),
                        new FunctionParameterSequenceType("function-reference", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function reference as created by the util:function function. " +
                                "It can be an arbitrary user-defined function, but it should take exactly 2 arguments: " +
                                "1) the current index key as found in the range index as an atomic value, 2) a sequence " +
                                "containing three int values: a) the overall frequency of the key within the node set, " +
                                "b) the number of distinct documents in the node set the key occurs in, " +
                                "c) the current position of the key in the whole list of keys returned."),
                        new FunctionParameterSequenceType("max-number-returned", Type.INTEGER, Cardinality.ZERO_OR_ONE , "The maximum number of returned keys")
                },
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the eval of the $function-reference"))
    };

    public IndexKeys(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        int arg = 0;
        final String field = args[arg++].getStringValue();
        String start = null;
        if (args.length == 4) {
            start = args[arg++].getStringValue();
        }
        try (final FunctionReference ref = (FunctionReference) args[arg++].itemAt(0)) {
            int max = -1;
            if (!args[arg].isEmpty()) {
                max = ((IntegerValue) args[arg].itemAt(0)).getInt();
            }

            final Sequence result = new ValueSequence();
            final RangeIndexWorker worker = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexName("range-index");
            Occurrences[] occur = worker.scanIndexByField(field, contextSequence == null ? context.getStaticallyKnownDocuments() : contextSequence.getDocumentSet(), start, max);
            final int len = (max != -1 && occur.length > max ? max : occur.length);
            final Sequence params[] = new Sequence[2];
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
            return result;
        }
    }
}

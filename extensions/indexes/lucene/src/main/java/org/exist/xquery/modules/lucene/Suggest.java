/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2019 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.lucene;

import org.apache.lucene.search.suggest.Lookup;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.util.List;

public class Suggest extends BasicFunction {

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("suggest", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Return a map of facet labels and counts for the result of a Lucene query.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "Name of the field"),
                            new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The string to pass to the suggester")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
                            "A map with each suggestion as key and the score as value")
            )
    };

    public Suggest(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final String field = args[0].getStringValue();
        final String query = args[1].getStringValue();
        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        try {
            final List<Lookup.LookupResult> lookup = index.getSuggesters().lookup(field, query, true, 10);
            if (lookup == null) {
                // return empty sequence if no suggester is defined for the field
                return Sequence.EMPTY_SEQUENCE;
            }
            final ValueSequence result = new ValueSequence(lookup.size());
            lookup.forEach((lookupResult) -> {
                final StringValue key = new StringValue(lookupResult.key.toString());
                result.add(key);
            });
            return result;
        } catch (IOException e) {
            throw new XPathException(this, LuceneModule.EXXQDYFT0002, "exception caught reading suggestions: " + e.getMessage());
        }
    }
}

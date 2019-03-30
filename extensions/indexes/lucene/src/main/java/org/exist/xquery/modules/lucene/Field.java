/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.modules.lucene;

import org.exist.dom.QName;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneMatch;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class Field extends BasicFunction {

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                new QName("field", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                "Returns the value of a field attached to a particular node obtained via a full text search." +
                        "Only fields listed in the 'fields' option of ft:query will be attached to the query result.",
                new SequenceType[]{
                        new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                                "the context node to check for attached fields"),
                        new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE,
                                "name of the field")
                },
                new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
                        "One or more string values corresponding to the values of the field attached")
            ),
            new FunctionSignature(
                    new QName("field", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Returns the value of a field attached to a particular node obtained via a full text search." +
                            "Only fields listed in the 'fields' option of ft:query will be attached to the query result." +
                            "Accepts an additional parameter to name the target type into which the field " +
                            "value should be cast. This is mainly relevant for fields having a different type than xs:string. " +
                            "As lucene does not record type information, numbers or dates would be returned as numbers by default.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                                    "the context node to check for attached fields"),
                            new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "name of the field"),
                            new FunctionParameterSequenceType("type", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "intended target type to cast the field value to. Casting may fail with a dynamic error.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                            "Sequence corresponding to the values of the field attached, cast to the desired target type")
            )
    };

    public Field(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        NodeValue nodeValue = (NodeValue) args[0].itemAt(0);
        if (nodeValue.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String fieldName = args[1].itemAt(0).getStringValue();

        int type = Type.STRING;
        if (getArgumentCount() == 3) {
            final String typeStr = args[2].itemAt(0).getStringValue();
            type = Type.getType(typeStr);
        }

        final NodeProxy proxy = (NodeProxy) nodeValue;
        Match match = proxy.getMatches();
        while (match != null) {
            if (match.getIndexId().equals(LuceneIndex.ID)) {
                return ((LuceneMatch)match).getField(fieldName, type);
            }
            match = match.getNextMatch();
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}

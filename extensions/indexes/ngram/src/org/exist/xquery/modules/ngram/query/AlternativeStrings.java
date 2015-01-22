/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xquery.modules.ngram.query;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.ngram.NGramSearch;

public class AlternativeStrings implements EvaluatableExpression, MergeableExpression {
    /**
     *
     */
    private final NGramSearch nGramSearch;
    final Set<String> strings;

    public AlternativeStrings(NGramSearch nGramSearch, final Set<String> strings) {
        this.nGramSearch = nGramSearch;
        this.strings = strings;
    }

    @Override
    public NodeSet eval(
        final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames, final NodeSet nodeSet,
        final int axis, final int expressionId) throws XPathException {
        NodeSet result = new ExtArrayNodeSet();
        for (String s : strings) {
            result.addAll(nGramSearch.fixedStringSearch(index, docs, qnames, s, nodeSet, axis));
        }
        result.iterate(); // ensure result is ready to use
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("AlternativeStrings(");
        for (String str : strings) {
            builder.append(", ");
            builder.append(str);
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public AlternativeStrings mergeWith(final WildcardedExpression otherExpression) {
        Set<String> concatenatedStrings = null;

        if (otherExpression instanceof FixedString) {
            FixedString fixedString = (FixedString) otherExpression;
            concatenatedStrings = new HashSet<String>(strings.size());
            for (String s : strings)
                concatenatedStrings.add(s + fixedString.fixedString);
        } else {
            AlternativeStrings otherAlternatives = (AlternativeStrings) otherExpression;
            concatenatedStrings = new HashSet<String>(strings.size() * otherAlternatives.strings.size());
            for (String s : strings)
                for (String os : otherAlternatives.strings)
                    concatenatedStrings.add(s + os);
        }

        return new AlternativeStrings(this.nGramSearch, concatenatedStrings);
    }

    @Override
    public boolean mergeableWith(final WildcardedExpression otherExpression) {
        return ((otherExpression instanceof FixedString) || (otherExpression instanceof AlternativeStrings));
    }
}

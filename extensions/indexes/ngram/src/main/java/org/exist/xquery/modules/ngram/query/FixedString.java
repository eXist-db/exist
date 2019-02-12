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
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.ngram.NGramSearch;

public class FixedString implements EvaluatableExpression, MergeableExpression {
    /**
     *
     */
    private final NGramSearch nGramSearch;
    final String fixedString;

    public FixedString(NGramSearch nGramSearch, final String fixedString) {
        this.nGramSearch = nGramSearch;
        this.fixedString = fixedString;
    }

    @Override
    public NodeSet eval(
        final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames, final NodeSet nodeSet,
        final int axis, final int expressionId) throws XPathException {
        return nGramSearch.fixedStringSearch(index, docs, qnames, fixedString, nodeSet, axis);
    }

    @Override
    public String toString() {
        return "FixedString(" + fixedString + ")";
    }

    @Override
    public EvaluatableExpression mergeWith(final WildcardedExpression otherExpression) {
        if (otherExpression instanceof FixedString) {
            FixedString otherString = (FixedString) otherExpression;
            return new FixedString(this.nGramSearch, fixedString + otherString.fixedString);
        } else {
            AlternativeStrings otherAlternatives = (AlternativeStrings) otherExpression;
            Set<String> strings = new HashSet<String>(otherAlternatives.strings.size());
            for (String os : otherAlternatives.strings)
                strings.add(fixedString + os);
            return new AlternativeStrings(this.nGramSearch, strings);
        }
    }

    @Override
    public boolean mergeableWith(final WildcardedExpression otherExpression) {
        return ((otherExpression instanceof FixedString) || (otherExpression instanceof AlternativeStrings));
    }
}

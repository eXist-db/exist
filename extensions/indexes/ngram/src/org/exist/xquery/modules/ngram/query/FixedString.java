package org.exist.xquery.modules.ngram.query;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
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
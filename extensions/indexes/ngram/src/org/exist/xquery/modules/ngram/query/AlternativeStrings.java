package org.exist.xquery.modules.ngram.query;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
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
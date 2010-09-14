package org.exist.xquery.modules.ngram.query;

import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.dom.EmptyNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.TerminatedException;

public class EmptyExpression implements EvaluatableExpression {
    @Override
    public NodeSet eval(
        final NGramIndexWorker index, final DocumentSet docs, final List<QName> qnames, final NodeSet nodeSet,
        final int axis, final int expressionId) throws TerminatedException {
        // TODO: Or match all?
        return new EmptyNodeSet();
    }

    @Override
    public String toString() {
        return "Empty()";
    }

}
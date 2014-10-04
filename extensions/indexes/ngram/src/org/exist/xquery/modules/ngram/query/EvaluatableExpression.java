package org.exist.xquery.modules.ngram.query;

import java.util.List;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.QName;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;

public interface EvaluatableExpression extends WildcardedExpression {
    public NodeSet eval(
        NGramIndexWorker index, DocumentSet docs, List<QName> qnames, NodeSet nodeSet, int axis, int expressionId)
        throws TerminatedException, XPathException;
}
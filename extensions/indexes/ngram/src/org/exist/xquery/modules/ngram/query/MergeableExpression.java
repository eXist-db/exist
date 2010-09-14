package org.exist.xquery.modules.ngram.query;

public interface MergeableExpression {
    public boolean mergeableWith(WildcardedExpression otherExpression);

    public WildcardedExpression mergeWith(WildcardedExpression otherExpression);
}
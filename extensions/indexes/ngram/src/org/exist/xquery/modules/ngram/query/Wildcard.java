package org.exist.xquery.modules.ngram.query;

public class Wildcard implements WildcardedExpression, MergeableExpression {
    final int minimumLength;
    final int maximumLength;

    public int getMinimumLength() {
        return minimumLength;
    };

    public int getMaximumLength() {
        return maximumLength;
    };

    public Wildcard(final int minimumLength, final int maximumLength) {
        this.minimumLength = minimumLength;
        this.maximumLength = maximumLength;
    }

    @Override
    public String toString() {
        return "Wildcard(" + minimumLength + ", " + maximumLength + ")";
    }

    @Override
    public WildcardedExpression mergeWith(final WildcardedExpression otherExpression) {
        Wildcard other = (Wildcard) otherExpression;
        int newMaximumLength = (this.maximumLength == Integer.MAX_VALUE || other.maximumLength == Integer.MAX_VALUE) ? Integer.MAX_VALUE
            : this.maximumLength + other.maximumLength;
        return new Wildcard(this.minimumLength + other.minimumLength, newMaximumLength);
    }

    @Override
    public boolean mergeableWith(final WildcardedExpression otherExpression) {
        return (otherExpression instanceof Wildcard);
    }
}
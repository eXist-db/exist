package org.exist.indexing;

import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import java.io.IOException;
import java.util.List;

/**
 * Simple interface for any range index that we
 * can query
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public interface QueryableRangeIndex {

    /**
     * The query operation to perform on the range index
     *
     * Enumeration of supported operators and optimized functions.
     */
    enum Operator {
        GT ("gt"),
        LT ("lt"),
        EQ ("eq"),
        GE ("ge"),
        LE ("le"),
        NE ("ne"),
        ENDS_WITH ("ends-with"),
        STARTS_WITH ("starts-with"),
        CONTAINS ("contains"),
        MATCH ("matches");

        private final String name;

        Operator(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static Operator fromName(final String name) {
            for(final Operator op : Operator.values()) {
                if(op.name.equals(name)) {
                    return op;
                }
            }

            throw new IllegalArgumentException("No Operator named: '" + name + "'");
        }
    }

    /**
     * Is the range index configured to index the node path
     *
     * @param broker
     * @param contextSequence
     * @param path The node path to check is configured for indexing
     * @return true if the path is configured for index
     */
    boolean isConfiguredFor(final DBBroker broker, final Sequence contextSequence, final NodePath path);

    /***
     * Perform a query against the range index
     */
    NodeSet query(int contextId, DocumentSet docs, NodeSet contextSet, List<QName> qnames, AtomicValue[] keys, Operator operator, int axis) throws IOException, XPathException;

    /**
     * Perform a Regular Expression match query against the range index
     *
     * @param regex The regular expression syntax supported by {@link java.util.regex.Pattern}
     * @param flags The regular expression syntax flags supported by {@link java.util.regex.Pattern}
     */
    NodeSet match(int contextId, DocumentSet docs, NodeSet contextSet, List<QName> qnames, String regex, int flags, int axis) throws IOException, XPathException;
}

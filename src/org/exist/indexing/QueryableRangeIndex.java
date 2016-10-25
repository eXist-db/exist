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
import java.util.Optional;

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
    enum OperatorType {
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

        OperatorType(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public static OperatorType fromName(final String name) {
            for(final OperatorType op : OperatorType.values()) {
                if(op.name.equals(name)) {
                    return op;
                }
            }

            throw new IllegalArgumentException("No Operator named: '" + name + "'");
        }
    }

    interface Operator {
        OperatorType getType();
    }

    static abstract class AbstractOperator implements Operator {
        @Override
        public String toString() {
            return getType().toString();
        }
    }

    static class MatchOperator extends AbstractOperator {
        private final Optional<String> regexFlags;

        public MatchOperator(final Optional<String> regexFlags) {
            this.regexFlags = regexFlags;
        }

        public Optional<String> getRegexFlags() {
            return regexFlags;
        }

        @Override
        public OperatorType getType() {
            return OperatorType.MATCH;
        }
    }

    static class OperatorFactory {
        public final static Operator GT = instance(OperatorType.GT);
        public final static Operator LT = instance(OperatorType.LT);
        public final static Operator EQ = instance(OperatorType.EQ);
        public final static Operator GE = instance(OperatorType.GE);
        public final static Operator LE = instance(OperatorType.LE);
        public final static Operator NE = instance(OperatorType.NE);
        public final static Operator ENDS_WITH = instance(OperatorType.ENDS_WITH);
        public final static Operator STARTS_WITH = instance(OperatorType.STARTS_WITH);
        public final static Operator CONTAINS = instance(OperatorType.CONTAINS);
        public final static Operator MATCHES_EXCLUDING_FLAGS = new MatchOperator(Optional.empty());

        private final static Operator instance(final OperatorType type) {
            return new AbstractOperator() {
                @Override
                public OperatorType getType() {
                    return type;
                }
            };
        }

        public final static Operator match(final String regexFlags) {
            return new MatchOperator(Optional.ofNullable(regexFlags).filter(s -> !s.isEmpty()));
        }

        public static Operator fromName(final String name) {
            final Operator op;
            switch(name) {
                case "gt":
                    op = GT;
                    break;

                case "lt":
                    op = LT;
                    break;

                case "eq":
                    op = EQ;
                    break;

                case "ge":
                    op = GE;
                    break;

                case "le":
                    op = LE;
                    break;

                case "ne":
                    op = NE;
                    break;

                case "ends-with":
                    op = ENDS_WITH;
                    break;

                case "starts-with":
                    op = STARTS_WITH;
                    break;

                case "contains":
                    op = CONTAINS;
                    break;

                case "matches":
                    op = MATCHES_EXCLUDING_FLAGS;
                    break;

                default:
                    throw new IllegalArgumentException("No operator named: " + name);
            }
            return op;
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
}

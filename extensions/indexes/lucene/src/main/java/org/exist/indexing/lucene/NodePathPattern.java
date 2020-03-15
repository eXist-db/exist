/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.storage.NodePath2;
import org.exist.util.FastStringBuffer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:stenlee@gmail.com">Stanislav Jordanov</a>
 * @version 1.0
 *
 * Class NodePathPattern is a replacement for class NodePath
 * in cases it was used not as a path, but as a path pattern.
 * Most notably this mis-design was employed in LuceneConfig and LuceneIndexConfig.
 *
 * This is required in order to implement the feature requested/discussed here:
 * see <a href="https://sourceforge.net/p/exist/mailman/message/36392026/">[Exist-open] Are more elaborate xpath expressions allowed in Lucene's index config...</a>
 *
 * After class NodePath2 was introduced and replaced NodePath in all cases related to Lucene index
 * element walking and matching, now all that is left in order to have the desired feature implemented
 * is implementing properly NodePathPattern.match methods, w/o modifying the originally used NodePath.
 */
public class NodePathPattern {

    private final NodePath qnPath;
    private final ArrayList<Predicate> predicates = new ArrayList<>();


    interface Predicate {
        boolean evaluate(NodePath2 nodePath, int elementIdx);
    }

    private final static class ConstTruePredicate implements Predicate {
        @Override
        public boolean evaluate(NodePath2 nodePath, int elementIdx) {
            return true;
        }
    }
    private final static Predicate CONST_TRUE_PREDICATE = new ConstTruePredicate();

    enum PredicateCode {
        EQUALS,      // =
        NOT_EQUALS,  // !=
        EQ,          // eq
        NE,          // ne
    }

    private static class SimpleAttrValuePredicate implements Predicate {
        private final PredicateCode pcode;
        private final String attrName;
        private final String attrVal;

        SimpleAttrValuePredicate(PredicateCode pcode, String attrName, String attrVal) {
            this.pcode = pcode;
            this.attrName = attrName;
            this.attrVal = attrVal;
        }

        @Override
        public boolean evaluate(NodePath2 nodePath, int elementIdx) {
            String val = nodePath.attribs(elementIdx).get(attrName);
            switch (pcode) {
                case EQUALS: // =
                case EQ: // eq
                    return Objects.equals(val, attrVal);
                case NOT_EQUALS: // !=
                    // actual attr val should be present but different:
                    return val != null && !Objects.equals(val, attrVal);
                case NE: // ne
                    // actual attr val may be null (i.e. not present) or present but different:
                    return !Objects.equals(val, attrVal);
                default:
                    assert false;
                    throw new IllegalArgumentException("PredicateCode " + pcode + " not handled!");
            }
        }
    }

    private final static class NegatePredicate implements Predicate {
        final Predicate negatedPredicate;

        NegatePredicate(Predicate negatedPredicate) {
            this.negatedPredicate = negatedPredicate;
        }

        @Override
        public boolean evaluate(NodePath2 nodePath, int elementIdx) {
            return !negatedPredicate.evaluate(nodePath, elementIdx);
        }
    }

    public NodePathPattern(Map<String, String> namespaces, String matchPattern) {
        qnPath = new NodePath();
        qnPath.setIncludeDescendants(false);
        parseXPathExpression(namespaces, matchPattern);
    }

    public NodePathPattern(final QName qname) {
        qnPath = new NodePath(qname);
    }

    private void parseXPathExpression(final Map<String, String> namespaces, final String matchPattern) {
        final FastStringBuffer token = new FastStringBuffer(matchPattern.length());
        int pos = 0;
        while (pos < matchPattern.length()) {
            final char ch = matchPattern.charAt(pos);
            switch (ch) {
                case '/':
                    final String next = token.toString();
                    token.setLength(0);
                    if (next.length() > 0) {
                        addSegment(namespaces, next);
                    }
                    if (matchPattern.charAt(++pos) == '/') {
                        qnPath.addComponent(NodePath.SKIP);
                        predicates.add(CONST_TRUE_PREDICATE);
                    }
                    break;
                default:
                    token.append(ch);
                    pos++;
                    break;
            }
        }
        if (token.length() > 0) {
            addSegment(namespaces, token.toString());
        }
    }

    private void addSegment(final Map<String, String> namespaces, final String segment) {
        String qname;
        int predBeg = segment.indexOf('[');
        Predicate pred;
        if (predBeg >= 0) {
            qname = segment.substring(0, predBeg);
            pred = parsePredicate(segment.substring(predBeg));
        } else {
            qname = segment.trim();
            pred = CONST_TRUE_PREDICATE;
        }

        if ("*".equals(qname)) {
            qnPath.addComponent(NodePath.WILDCARD);
        } else {
            qnPath.addComponent(namespaces, qname);
        }
        predicates.add(pred);
    }

    private Predicate parsePredicate(String input) {
        if (!input.startsWith("[") || !input.endsWith("]")) {
            throw new IllegalArgumentException("Bad predicate spec: " + input
                    + "\nPredicate should be enclosed in []-brackets");
        }
        input = input.substring(1, input.length() - 1).trim(); // to skip the [ and ]

        boolean negate = false;
        if (input.startsWith("fn:not(")) {
            input = input.substring("fn:not".length()).trim();
            negate = true;
        }

        if (negate) {
            if (!input.startsWith("(") || !input.endsWith(")")) {
                throw new IllegalArgumentException("Bad predicate spec: " + input
                        + "\nArgument of fn:not should be enclosed in () parentheses");
            }
            input = input.substring(1, input.length() - 1).trim(); // to skip the ( and )
        }

        // So far we're supporting only `@attr OP 'value'` predicates, where OP is one of:
        String[] ops = { "!=", "=", " ne ", " eq "};
        PredicateCode[] pcodes = { PredicateCode.NOT_EQUALS, PredicateCode.EQUALS, PredicateCode.NE, PredicateCode.EQ };
        PredicateCode pcode = null;
        int opIdx = 0, opIdxEnd = 0;
        for (int i=0; i < ops.length; ++i) {
            opIdx = input.indexOf(ops[i]);
            if (opIdx >= 1) { // as input should start with `@`
                pcode = pcodes[i];
                opIdxEnd = opIdx + ops[i].length();
                break;
            }
        }

        if (!input.startsWith("@") || pcode == null) {
            throw new IllegalArgumentException("Bad predicate spec: " + input
                    + "\nOnly [@attr OP 'value'] and [fn:not(@attr OP 'value')] are supported, where OP is one of "
                    + String.join(", ", ops));
        }
        String name = input.substring(1, opIdx).trim(); // start at 1 to skip the leading `@`
        String val = input.substring(opIdxEnd).trim();

        if (!(val.startsWith("\'") && val.endsWith("\'") || val.startsWith("\"") && val.endsWith("\""))) {
            throw new IllegalArgumentException("Bad predicate spec: " + input + "\nAttribute value not in quotes");
        } else {
            val = val.substring(1, val.length() - 1); // strip the quotes
        }

        Predicate res = new SimpleAttrValuePredicate(pcode, name, val);
        return negate ? new NegatePredicate(res) : res;
    }

    public int length() {
        return qnPath.length();
    }

    public QName getLastComponent() {
        return qnPath.getLastComponent();
    }

    public boolean hasWildcard() {
        return qnPath.hasWildcard();
    }


    public final boolean match(final QName qname) {
        return qnPath.match(qname);
    }

    public final boolean match(final NodePath other) {
        return match(other, 0);
    }

    private final boolean match(final NodePath o, final int from_pos) {
        // TODO cast NodePath to NodePath2 and do 'extended' matching
        final NodePath2 other = (NodePath2) o;
        final int other_len = other.length();
        final int len = qnPath.length();
        boolean skip = false;
        int i = 0;
        QName components_i = null;
        for (int j = from_pos; j < other_len; j++) {
            if (i == len) {
                return qnPath.includeDescendants();
            }
            if (components_i == null)
                components_i = qnPath.getComponent(i);

            if (components_i == NodePath.SKIP) {
                components_i = qnPath.getComponent(++i);
                skip = true;
            }
            if((components_i == NodePath.WILDCARD || other.getComponent(j).compareTo(components_i) == 0)
                && predicates.get(i).evaluate(other, j)
                && (!skip || j + 1 == other_len || other.getComponent(j + 1).compareTo(components_i) != 0
                                                || !predicates.get(i).evaluate(other, j + 1))) {
                ++i;
                components_i = null;
                skip = false;
            } else if (skip) {
                continue;
            } else {
                return false;
            }
        }

        return (i == len);
    }

    @Override
    public boolean equals(final Object obj) {
        return qnPath.equals(obj);
    }

    @Override
    public int hashCode() {
        return qnPath.hashCode();
    }
}

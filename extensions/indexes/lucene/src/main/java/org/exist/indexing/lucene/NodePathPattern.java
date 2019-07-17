/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.indexing.lucene;

//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.storage.NodePath2;
import org.exist.util.FastStringBuffer;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author <a href="mailto:stenlee@gmail.com">Stanislav Jordanov</a>
 * @version 1.0
 *
 * Class NodePathPattern is a replacement for class NodePath
 * in cases it was used not as a path, but as a path pattern.
 * Most notably this mis-design was employed in LuceneConfig and LuceneIndexConfig.
 *
 * This is required in order to implement the feature requested/discussed here:
 * @see <a href="https://sourceforge.net/p/exist/mailman/message/36392026/">[Exist-open] Are more elaborate xpath expressions allowed in Lucene's index config &lt;text match='...'/&gt;</a>
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

    private final static Predicate CONST_TRUE_PREDICATE = new Predicate() {
        @Override
        public boolean evaluate(NodePath2 nodePath, int elementIdx) {
            return true;
        }
    };

    static class SimpleAttrEqValuePredicate implements Predicate {
        private final String attrName;
        private final String attrVal;

        SimpleAttrEqValuePredicate(String attrName, String attrVal) {
            this.attrName = attrName;
            this.attrVal = attrVal;
        }

        @Override
        public boolean evaluate(NodePath2 nodePath, int elementIdx) {
            String val = nodePath.attribs(elementIdx).get(attrName);
            return val != null ? val.equals(attrVal) : attrVal == null;
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
        Predicate pred = null;
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
        if (!input.startsWith("[") || !input.endsWith("]") || input.charAt(1) != '@') {
            throw new IllegalArgumentException("Bad predicate spec: " + input + "\nOnly [@attr=value] is supported");
        }

        // So far we're supporting only [@attr=value] predicates:
        int eqIdx = input.indexOf('=');
        if (eqIdx < 0) {
            throw new IllegalArgumentException("Bad predicate spec: " + input + "\nOnly [@attr=value] is supported");
        }
        String name = input.substring(2, eqIdx).trim(); // 2 is to skip the leading [@
        String val = input.substring(eqIdx + 1, input.length() - 1).trim(); // -1 is to skip the trailing ]

        if (!(val.startsWith("\'") && val.endsWith("\'") || val.startsWith("\"") && val.endsWith("\""))) {
            throw new IllegalArgumentException("Bad predicate spec: " + input + "\nAttribute value not in quotes");
        } else {
            val = val.substring(1, val.length() - 1); // strip the quotes
        }

        return new SimpleAttrEqValuePredicate(name, val);
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

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
package org.exist.dom.persistent;

import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.annotation.Nullable;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AVLTreeNodeSet#add(NodeProxy)} drops a node equal to one in the set, without merging its matches, unlike the
 * array node sets. So the set operations that build their result in one must merge matches themselves: they never add a
 * second copy of a node.
 */
public class AVLTreeNodeSetMatchesTest {

    private static final DocumentImpl DOCUMENT = document();

    private static final class TestMatch extends Match {
        private final String term;

        TestMatch(final NodeId nodeId, final String term) {
            super(0, nodeId, term);
            this.term = term;
        }

        private TestMatch(final TestMatch match) {
            super(match);
            this.term = match.term;
        }

        @Override
        public Match createInstance(final int contextId, final NodeId nodeId, @Nullable final String matchTerm) {
            return new TestMatch(nodeId, matchTerm);
        }

        @Override
        public Match newCopy() {
            return new TestMatch(this);
        }

        @Override
        public String getIndexId() {
            return "test";
        }
    }

    private static DocumentImpl document() {
        final DocumentImpl document = createNiceMock(DocumentImpl.class);
        expect(document.getDocId()).andStubReturn(1);
        replay(document);
        return document;
    }

    private static NodeProxy node(final int id, @Nullable final String term) {
        final NodeId nodeId = new DLN(id);
        final NodeProxy node = new NodeProxy(null, DOCUMENT, nodeId, Node.ELEMENT_NODE);
        if (term != null) {
            node.addMatch(new TestMatch(nodeId, term));
        }
        return node;
    }

    private static AVLTreeNodeSet setOf(final NodeProxy... nodes) {
        final AVLTreeNodeSet set = new AVLTreeNodeSet();
        for (final NodeProxy node : nodes) {
            set.add(node);
        }
        return set;
    }

    private static String terms(final NodeProxy node) {
        final StringBuilder terms = new StringBuilder();
        for (Match match = node.getMatches(); match != null; match = match.getNextMatch()) {
            terms.append(((TestMatch) match).term).append(' ');
        }
        return terms.toString().trim();
    }

    @Test
    public void addDropsAnEqualNodeWithoutMergingItsMatches() {
        final AVLTreeNodeSet set = setOf(node(1, "a"), node(1, "b"));
        assertEquals(1, set.getLength());
        assertEquals("a", terms(set.get(node(1, null))));
    }

    @Test
    public void intersectionHasTheMatchesOfBothSets() {
        final NodeProxy left = node(1, "a");
        final NodeProxy right = node(1, "b");

        final NodeProxy found = setOf(left).intersection(setOf(right)).get(left);

        assertEquals("b a", terms(found));
        assertNotSame(left, found);
        assertEquals("a", terms(left));
        assertEquals("b", terms(right));
    }

    @Test
    public void deepIntersectionOfOneNodeHasTheMatchesOfBothSets() {
        final NodeProxy left = node(1, "a");
        final NodeProxy right = node(1, "b");

        final NodeProxy found = setOf(left).deepIntersection(setOf(right)).get(left);

        assertEquals(2, terms(found).split(" ").length);
        assertEquals("a", terms(left));
        assertEquals("b", terms(right));
    }

    @Test
    public void exceptKeepsTheMatchesOfTheNodesLeft() {
        final NodeProxy kept = node(1, "a");
        final NodeProxy found = setOf(kept, node(2, "b")).except(setOf(node(2, "c"))).get(kept);
        assertEquals("a", terms(found));
        assertNull(setOf(kept).except(setOf(node(1, null))).get(kept));
    }
}

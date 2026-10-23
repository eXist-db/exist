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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MatchChainTest {

    private static final class TestMatch extends Match {
        TestMatch(final NodeId nodeId, final String term, final int frequency) {
            super(0, nodeId, term, frequency);
        }

        private TestMatch(final TestMatch match) {
            super(match);
        }

        @Override
        public Match createInstance(final int contextId, final NodeId nodeId, @Nullable final String matchTerm) {
            return new TestMatch(nodeId, matchTerm, 1);
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

    private static Match match(final String nodeId, final String term, final int... offsets) {
        final Match match = new TestMatch(new DLN(nodeId), term, Math.max(offsets.length, 2));
        for (final int offset : offsets) {
            match.addOffset(offset, 1);
        }
        return match;
    }

    private static String chain(final Match chain) {
        final StringBuilder buf = new StringBuilder();
        for (Match m = chain; m != null; m = m.getNextMatch()) {
            buf.append(m.getNodeId()).append('/').append(m.getOffsets().stream().map(o -> String.valueOf(o.getOffset()))
                    .reduce((a, b) -> a + "," + b).orElse("")).append(' ');
        }
        return buf.toString().trim();
    }

    @Test
    void addLeavesTheChainAndTheMatchAsTheyWere() {
        final Match one = Match.add(null, match("1.1", "a", 0));
        final Match two = Match.add(one, match("1.2", "b", 5));
        assertEquals("1.1/0", chain(one));
        assertEquals("1.2/5 1.1/0", chain(two));
    }

    @Test
    void addingTheHeadOfAnotherChainTakesTheMatchOnly() {
        final Match other = Match.add(Match.add(null, match("1.1", "a", 0)), match("1.2", "b", 5));
        final Match taken = Match.add(null, other);
        assertEquals("1.2/5", chain(taken));
        assertEquals("1.2/5 1.1/0", chain(other));
    }

    @Test
    void anEqualMatchIsMergedIntoACopyAndTheRestShared() {
        final Match chain = Match.add(Match.add(Match.add(null, match("1.1", "a", 0)), match("1.2", "b", 5)),
                match("1.3", "c", 9));
        final Match merged = Match.add(chain, match("1.2", "b", 7));
        assertEquals("1.3/9 1.2/5,7 1.1/0", chain(merged));
        assertEquals("1.3/9 1.2/5 1.1/0", chain(chain));
        assertSame(chain.getNextMatch().getNextMatch(), merged.getNextMatch().getNextMatch());
    }

    @Test
    void aCopysOffsetsAreItsOwn() {
        final Match original = new TestMatch(new DLN("1.1"), "a", 4);
        original.addOffset(0, 1);
        final Match copy = original.newCopy();
        copy.addOffset(3, 1);
        original.addOffset(6, 1);
        assertEquals("1.1/0,3", chain(copy));
        assertEquals("1.1/0,6", chain(original));
    }

    @Test
    void nodesSharingAChainKeepTheirOwnMatches() {
        final NodeProxy a = new NodeProxy(null, null, new DLN("1.1"), Node.ELEMENT_NODE, -1);
        a.addMatch(match("1.1", "a", 0));
        final NodeProxy b = new NodeProxy(null, null, new DLN("1.1"), Node.ELEMENT_NODE, -1);
        b.setMatches(a.getMatches());
        b.addMatch(match("1.1", "a", 4));
        assertEquals("1.1/0", chain(a.getMatches()));
        assertEquals("1.1/0,4", chain(b.getMatches()));
    }
}

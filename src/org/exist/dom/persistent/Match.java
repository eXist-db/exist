/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.persistent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.exist.numbering.NodeId;

/**
 * Used to track matches throughout the query.
 * <p/>
 * Index may add a match object to every {@link org.exist.dom.persistent.NodeProxy}
 * that triggered a match for every term matched. The
 * Match object contains the nodeId of the text node that triggered the
 * match, the string value of the matching term and a frequency count,
 * indicating the frequency of the matching term string within the corresponding
 * single text node.
 * <p/>
 * All path operations copy existing match objects, i.e. the match objects
 * are copied to the selected descendant or child nodes. This means that
 * every NodeProxy being the direct or indirect result of a
 * selection will have one or more match objects, indicating which text nodes
 * among its descendant nodes contained a match.
 *
 * @author wolf
 */
public abstract class Match implements Comparable<Match> {

    public static final class Offset implements Comparable<Offset> {
        private final int offset;
        private final int length;

        public Offset(final int offset, final int length) {
            this.offset = offset;
            this.length = length;
        }

        public int getOffset() {
            return offset;
        }

        public int getLength() {
            return length;
        }

        @Override
        public int compareTo(final Offset other) {
            return this.offset - other.offset;
        }

        public boolean overlaps(final Offset other) {
            return (other.offset >= offset && other.offset < offset + length)
                || (offset >= other.offset && offset < other.offset + other.length);
        }
    }

    private final int context;
    protected final NodeId nodeId;
    private final String matchTerm;
    private int[] offsets;
    private int[] lengths;

    private int currentOffset = 0;

    protected Match nextMatch = null;

    protected Match(final int contextId, final NodeId nodeId, final String matchTerm) {
        this(contextId, nodeId, matchTerm, 1);
    }

    protected Match(final int contextId, final NodeId nodeId, final String matchTerm, final int frequency) {
        this.context = contextId;
        this.nodeId = nodeId;
        this.matchTerm = matchTerm;
        this.offsets = new int[frequency];
        this.lengths = new int[frequency];
    }

    protected Match(final Match match) {
        this.context = match.context;
        this.nodeId = match.nodeId;
        this.matchTerm = match.matchTerm;
        this.offsets = match.offsets;
        this.lengths = match.lengths;
        this.currentOffset = match.currentOffset;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public int getFrequency() {
        return currentOffset;
    }

    public int getContextId() {
        return context;
    }

    public abstract Match createInstance(final int contextId, final NodeId nodeId, final String matchTerm);
    public abstract Match newCopy();
    public abstract String getIndexId();

    public void addOffset(final int offset, final int length) {
        if(currentOffset == offsets.length) {
            final int noffsets[] = new int[currentOffset + 1];
            System.arraycopy(offsets, 0, noffsets, 0, currentOffset);
            offsets = noffsets;
            final int nlengths[] = new int[currentOffset + 1];
            System.arraycopy(lengths, 0, nlengths, 0, currentOffset);
            lengths = nlengths;
        }
        offsets[currentOffset] = offset;
        lengths[currentOffset++] = length;
    }

    private void addOffset(final Offset offset) {
        addOffset(offset.offset, offset.length);
    }

    private void addOffsets(final Collection<Offset> offsets) {
        offsets.forEach(this::addOffset);
    }

    public Offset getOffset(final int pos) {
        return new Offset(offsets[pos], lengths[pos]);
    }

    public List<Offset> getOffsets() {
        final List<Offset> result = new ArrayList<>(currentOffset);
        for(int i = 0; i < currentOffset; i++) {
            result.add(getOffset(i));
        }
        return result;
    }

    /**
     * Constructs a match starting with this match and continued by the other match if possible
     *
     * @param other a match continuing this match
     * @return a match starting with this match and continued by the other match
     * if such a match exists or null if no continuous match found
     */
    public Match continuedBy(final Match other) {
        return followedBy(other, 0, 0);
    }

    /**
     * Constructs a match starting with this match and followed by the other match if possible
     *
     * @param other       a match following this match
     * @param minDistance the minimum distance between this and the other match
     * @param maxDistance the maximum distance between this and the other match
     * @return a match starting with this match and followed by
     * the other match in the specified distance range if such
     * a match exists or null if no such match found
     */
    public Match followedBy(final Match other, final int minDistance, final int maxDistance) {
        final List<Offset> newMatchOffsets = new LinkedList<>();
        for(int i = 0; i < currentOffset; i++) {
            for(int j = 0; j < other.currentOffset; j++) {
                final int distance = other.offsets[j] - (offsets[i] + lengths[i]);
                if(distance >= minDistance && distance <= maxDistance) {
                    newMatchOffsets.add(new Offset(offsets[i], lengths[i] + distance + other.lengths[j]));
                }
            }
        }

        if(newMatchOffsets.isEmpty()) {
            return null;
        }

        final int wildCardSize = newMatchOffsets.get(0).length - matchTerm.length() - other.matchTerm.length();
        final StringBuilder matched = new StringBuilder(matchTerm);
        for(int ii = 0; ii < wildCardSize; ii++) {
            matched.append('?');
        }
        matched.append(other.matchTerm);
        final Match result = createInstance(context, nodeId, matched.toString());
        result.addOffsets(newMatchOffsets);
        return result;
    }

    /**
     * Expand the match backwards by at least minExpand up to maxExpand characters.
     * The match is expanded as much as possible.
     *
     * @param minExpand The minimum number of characters to expand this match by
     * @param maxExpand The maximum number of characters to expand this match by
     * @return The expanded match if possible, or null if no offset is far enough from the start.
     */
    public Match expandBackward(final int minExpand, final int maxExpand) {
        Match result = null;
        for(int i = 0; i < currentOffset; i++) {
            if(offsets[i] - minExpand >= 0) {
                if(result == null) {
                    final StringBuilder matched = new StringBuilder();
                    for(int ii = 0; ii < minExpand; ii++) {
                        matched.append('?');
                    }
                    matched.append(matchTerm);
                    result = createInstance(context, nodeId, matched.toString());
                }
                final int expand = Math.min(offsets[i], maxExpand);
                result.addOffset(offsets[i] - expand, lengths[i] + expand);
            }
        }
        return result;
    }

    /**
     * Expand the match forward by at least minExpand up to maxExpand characters.
     * The match is expanded as much as possible.
     *
     * @param minExpand  The minimum number of characters to expand this match by
     * @param maxExpand  The maximum number of characters to expand this match by
     * @param dataLength The length of the valued of the node, limiting the expansion
     * @return The expanded match if possible, or null if no offset is far enough from the end.
     */
    public Match expandForward(final int minExpand, final int maxExpand, final int dataLength) {
        Match result = null;
        for(int i = 0; i < currentOffset; i++) {
            if(offsets[i] + lengths[i] + minExpand <= dataLength) {
                final int expand = Math.min(dataLength - offsets[i] - lengths[i], maxExpand);
                if(result == null) {
                    final StringBuilder matched = new StringBuilder(matchTerm);
                    for(int ii = 0; ii < expand; ii++) {
                        matched.append('?');
                    }
                    result = createInstance(context, nodeId, matched.toString());
                }
                result.addOffset(offsets[i], lengths[i] + expand);
            }
        }
        return result;
    }

    private Match filterOffsets(final Predicate<Offset> predicate) {
        final Match result = createInstance(context, nodeId, matchTerm);
        getOffsets().stream().filter(predicate).forEach(result::addOffset);
        if(result.currentOffset == 0) {
            return null;
        } else {
            return result;
        }
    }

    /**
     * Creates a match containing only those offsets starting at the given position.
     *
     * @param pos Required offset
     * @return a match containing only offsets starting at the given position,
     * or null if no such offset exists.
     */
    public Match filterOffsetsStartingAt(final int pos) {
        return filterOffsets(offset -> offset.offset == pos);
    }

    /**
     * Creates a match containing only those offsets ending at the given position.
     *
     * @param pos Required position of the end of the matches
     * @return A match containing only offsets ending at the given position,
     * or null if no such offset exists.
     */
    public Match filterOffsetsEndingAt(final int pos) {
        return filterOffsets(offset -> offset.offset + offset.length == pos);
    }

    /**
     * Creates a match containing only non-overlapping offsets,
     * preferring longer matches, and then matches from left to right.
     *
     * @return a match containing only non-overlapping offsets
     */
    public Match filterOutOverlappingOffsets() {
        if(currentOffset == 0) {
            return newCopy();
        }
        final List<Offset> newMatchOffsets = getOffsets();
        Collections.sort(newMatchOffsets, (o1, o2) -> {
            final int lengthDiff = o2.length - o1.length;
            if(lengthDiff != 0) {
                return lengthDiff;
            } else {
                return o1.offset - o2.offset;
            }
        });
        final List<Offset> nonOverlappingMatchOffsets = new LinkedList<>();
        nonOverlappingMatchOffsets.add(newMatchOffsets.remove(0));
        for(final Offset o : newMatchOffsets) {
            boolean overlapsExistingOffset = false;
            for(final Offset eo : nonOverlappingMatchOffsets) {
                if(eo.overlaps(o)) {
                    overlapsExistingOffset = true;
                    break;
                }
            }
            if(!overlapsExistingOffset) {
                nonOverlappingMatchOffsets.add(o);
            }
        }
        final Match result = createInstance(context, nodeId, matchTerm);
        result.addOffsets(nonOverlappingMatchOffsets);
        return result;
    }

    /**
     * Return true if there's a match starting at the given
     * character position.
     *
     * @param pos the position
     * @return true if a match starts at the given position
     */
    public boolean hasMatchAt(final int pos) {
        for(int i = 0; i < currentOffset; i++) {
            if(offsets[i] == pos) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given position is within a match.
     *
     * @param pos the position
     * @return true if the given position is within a match
     */
    public boolean hasMatchAround(final int pos) {
        for(int i = 0; i < currentOffset; i++) {
            if(offsets[i] + lengths[i] >= pos) {
                return true;
            }
        }
        return false;
    }

    public void mergeOffsets(final Match other) {
        for(int i = 0; i < other.currentOffset; i++) {
            if(!hasMatchAt(other.offsets[i])) {
                addOffset(other.offsets[i], other.lengths[i]);
            }
        }
    }

    public Match getNextMatch() {
        return nextMatch;
    }

    public static boolean matchListEquals(final Match m1, final Match m2) {
        Match n1 = m1;
        Match n2 = m2;
        while(n1 != null) {
            if(n2 == null || n1 != n2) {
                return false;
            }
            n1 = n1.nextMatch;
            n2 = n2.nextMatch;
        }
        return true;
    }

    @Override
    public boolean equals(final Object other) {
        if(!(other instanceof Match)) {
            return false;
        }
        final Match om = (Match) other;
        return om.matchTerm != null &&
            om.matchTerm.equals(matchTerm) &&
            om.nodeId.equals(nodeId);
    }

    public boolean matchEquals(final Match other) {
        if(this == other) {
            return true;
        }
        return
            (nodeId == other.nodeId || nodeId.equals(other.nodeId)) &&
                matchTerm.equals(other.matchTerm);
    }

    /**
     * Used to sort matches. Terms are compared by their string
     * length to have the longest string first.
     *
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final Match other) {
        return matchTerm.compareTo(other.matchTerm);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        if(matchTerm != null) {
            buf.append(matchTerm);
        }

        for(int i = 0; i < currentOffset; i++) {
            buf.append(" [");
            buf.append(offsets[i]).append(':').append(lengths[i]);
            buf.append("]");
        }

        if(nextMatch != null) {
            buf.append(' ').append(nextMatch.toString());
        }
        return buf.toString();
    }
}

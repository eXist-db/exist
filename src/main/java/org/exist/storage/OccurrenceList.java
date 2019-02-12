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
package org.exist.storage;

import org.exist.numbering.NodeId;
import org.exist.util.FastQSort;

/**
 * Simple list of node ids and their offsets within the text sequence.
*/
public class OccurrenceList {

    private NodeId nodes[] = new NodeId[4];
    private int offsets[] = new int[4];

    private int position = 0;

    public void add(NodeId id, int offset) {
        ensureCapacity(position);
        nodes[position] = id;
        offsets[position++] = offset;
    }

    public NodeId getNode(int pos) {
        return nodes[pos];
    }

    public int getOffset(int pos) {
        return offsets[pos];
    }
    
    public int getSize() {
        return position;
    }

    public int getTermCount() {
        int count = 1;
        for (int i = 1; i < position; i++) {
            if (!nodes[i].equals(nodes[i - 1]))
                {count++;}
        }
        return count;
    }

    public int getOccurrences(int start) {
        int count = 1;
        for (int i = start + 1; i < position; i++) {
            if (nodes[i].equals(nodes[start]))
                {count++;}
            else
                {break;}
        }
        return count;
    }

    public boolean contains(NodeId id) {
        for (int i = 0; i < position; i++)
            if (nodes[i].equals(id))
                {return true;}
        return false;
    }

    private void ensureCapacity(int count) {
        if (count == nodes.length) {
            NodeId[] nn = new NodeId[count * 2];
            int[] no = new int[nn.length];
            System.arraycopy(nodes, 0, nn, 0, count);
            System.arraycopy(offsets, 0, no, 0, count);
            nodes = nn;
            offsets = no;
        }
    }

    public void sort() {
        FastQSort.sort(nodes,0,position - 1,offsets);
    }
}

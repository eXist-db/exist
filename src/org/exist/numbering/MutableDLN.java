/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.numbering;

public class MutableDLN extends DLN {

    // offset pointing to the first bit of the
    // last level written
    protected int lastLevelOffset = -1;

    public MutableDLN() {
        this(1);
    }

    public MutableDLN(int id) {
        bits = new byte[1];
        addLevelId(id);
    }

    public MutableDLN(int[] id) {
        this(id[0]);
        for (int i = 1; i < id.length; i++)
            addLevelId(id[i]);
    }

    public MutableDLN(MutableDLN other) {
        super(other);
        this.lastLevelOffset = other.lastLevelOffset;
    }

    public MutableDLN(int units, byte[] data, int startOffset) {
        super(units, data, startOffset);
    }

    public MutableDLN newChild() {
        MutableDLN child = new MutableDLN(this);
        child.addLevelId(1);
        return child;
    }

    public MutableDLN nextSibling() {
        MutableDLN sibling = new MutableDLN(this);
        sibling.incrementLevelId();
        return sibling;
    }

    public void addLevelId(int levelId) {
        lastLevelOffset = bitIndex;

        setCurrentLevelId(levelId);
    }

    public void incrementLevelId() {
        bitIndex = lastLevelOffset;
        setCurrentLevelId(getLevelId(lastLevelOffset + 1) + 1);
    }

    public static void main(String[] args) {
        MutableDLN id = new MutableDLN();
        id.setLevelId(0, 8);
        System.out.println("ID: " + id.toBitString() + " = " + id.getLevelId(0));
        id.setLevelId(0, 0);
        for (int i = 0; i < 100; i++) {
            id.incrementLevelId();
            System.out.println("ID: " + id.toBitString() + " = " + id.getLevelId(0));
        }
        id.addLevelId(0);
        System.out.println("ID: " + id.toBitString() + " = " + id.toString());

        id = new MutableDLN(5);
        id.addLevelId(7);
        id.addLevelId(120);
        System.out.println("Levels: " + id.getLevelCount());
        System.out.println("ID: " + id.toString() + " = " + id.toBitString());
    }
}

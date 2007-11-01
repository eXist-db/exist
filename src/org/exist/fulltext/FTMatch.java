/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id$
 */
package org.exist.fulltext;

import org.exist.dom.Match;
import org.exist.numbering.NodeId;

public class FTMatch extends Match {

    public FTMatch(int contextId, NodeId nodeId, String matchTerm) {
        super(contextId, nodeId, matchTerm);
    }

    public FTMatch(int contextId, NodeId nodeId, String matchTerm, int frequency) {
        super(contextId, nodeId, matchTerm, frequency);
    }

    public FTMatch(Match match) {
        super(match);
    }

    public Match createInstance(int contextId, NodeId nodeId, String matchTerm) {
        return new FTMatch(contextId, nodeId, matchTerm);
    }

    public Match newCopy() {
        return new FTMatch(this);
    }

    public String getIndexId() {
        return FTIndex.ID;
    }
}
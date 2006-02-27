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

import org.exist.storage.io.VariableByteInput;

import java.io.IOException;

/**
 * Implementation of {@link NodeIdFactory} for DLN-based
 * node ids.
 */
public class DLNFactory implements NodeIdFactory {

    public NodeId createInstance() {
        return new DLN();
    }

    public NodeId createFromStream(VariableByteInput is) throws IOException {
        return new DLN(is);
    }

    public NodeId createFromData(int sizeHint, byte[] data, int startOffset) {
        return new DLN(sizeHint, data, startOffset);
    }

    public NodeId documentNodeId() {
        return DLN.DOCUMENT_NODE;
    }

    public int lengthInBytes(int units, byte[] data, int startOffset) {
        return DLN.getLengthInBytes(units, data, startOffset);
    }
}

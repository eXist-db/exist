/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
package org.exist.indexing.range;

import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.storage.NodePath;

public class RangeIndexDoc {

    private NodeId nodeId;
    private QName qname;
    private NodePath path;
    private TextCollector collector;
    private RangeIndexConfigElement config;
    private long address = -1;

    public RangeIndexDoc(NodeId nodeId, QName qname, NodePath path, TextCollector collector, RangeIndexConfigElement config) {
        this.nodeId = nodeId;
        this.qname = qname;
        this.path = path;
        this.collector = collector;
        this.config = config;
    }

    public void setAddress(long address) {
        this.address = address;
    }

    public long getAddress() {
        return address;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public QName getQName() {
        return qname;
    }

    public NodePath getPath() {
        return path;
    }

    public TextCollector getCollector() {
        return collector;
    }

    public RangeIndexConfigElement getConfig() {
        return config;
    }
}
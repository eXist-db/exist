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
package org.exist.indexing;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ExtNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.NodeTest;

/**
 * Core interface for structural indexes. The structural index provides access to elements and attributes
 * through their name and relation.
 */
public interface StructuralIndex {

    public final static String STRUCTURAL_INDEX_ID = "structural-index";

    public final static String DEFAULT_CLASS = "org.exist.storage.structural.NativeStructuralIndex";

    public boolean matchElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector);

    public boolean matchDescendantsByTagName(byte type, QName qname, int axis,
        DocumentSet docs, ExtNodeSet contextSet, int contextId);

    public NodeSet findElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector);

    public NodeSet findElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector, Expression parent);

    public NodeSet findDescendantsByTagName(byte type, QName qname, int axis,
        DocumentSet docs, NodeSet contextSet,  int contextId);

    public NodeSet findDescendantsByTagName(byte type, QName qname, int axis,
                                            DocumentSet docs, NodeSet contextSet,  int contextId, Expression parent);

    public NodeSet findAncestorsByTagName(byte type, QName qname, int axis,
            DocumentSet docs, NodeSet contextSet, int contextId);

    /**
     * Find all nodes matching a given node test, axis and type. Used to evaluate wildcard
     * expressions like //*, //pfx:*.
     *
     * @param type node type
     * @param contextId the context id
     * @param axis the xpath axis
     * @param test  node test
     * @param useSelfAsContext use self as context or not
     * @param docs the docs to execute the test against
     * @param contextSet  the NodeSet contextSet
     * @return all nodes matching the given node test, axis and type
     */
    public NodeSet scanByType(byte type, int axis, NodeTest test, boolean useSelfAsContext, 
            DocumentSet docs, NodeSet contextSet, int contextId);
}

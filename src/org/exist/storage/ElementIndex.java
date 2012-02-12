/*
 * eXist Open Source Native XML Database 
 * Copyright (C) 2001-06 The eXist Project
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.util.Occurrences;
import org.exist.xquery.NodeSelector;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Observable;
import java.util.TreeMap;

/** base class for {@link org.exist.storage.structural.NativeStructuralIndex} */
public abstract class ElementIndex extends Observable {

    protected static Logger LOG = Logger.getLogger(ElementIndex.class.getName());

    /** The broker that is using this value index */
    protected DBBroker broker;

    protected TreeMap<QName, ArrayList<NodeProxy>> pending = new TreeMap<QName, ArrayList<NodeProxy>>();

    /** The current document */
    protected DocumentImpl doc;

    protected boolean inUpdateMode = false;

    public ElementIndex(DBBroker broker) {
        this.broker = broker;
    }

    public void setDocument(DocumentImpl doc) {
        if (pending.size() > 0 && this.doc.getDocId() != doc.getDocId()) {
            LOG.error("Document changed but pending had " + pending.size(), new Throwable());
            pending.clear();
        }
        this.doc = doc;
    }

    public void setInUpdateMode(boolean update) {
        inUpdateMode = update;
    }

    public abstract NodeSet findElementsByTagName(byte type, DocumentSet docs, QName qname,	NodeSelector selector);

    public abstract NodeSet findDescendantsByTagName(byte type, QName qname, int axis, DocumentSet docs, 
        ExtNodeSet contextSet,  int contextId);

    public abstract Occurrences[] scanIndexedElements(Collection collection, boolean inclusive) 
        throws PermissionDeniedException;

    public abstract boolean matchElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector);

    public abstract boolean matchDescendantsByTagName(byte type, QName qname, int axis, DocumentSet docs, 
        ExtNodeSet contextSet,  int contextId);

}

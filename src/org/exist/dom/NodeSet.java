
/* eXist Open Source Native XML Database
 * Copyright (C) 2000-01,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id:
 */
package org.exist.dom;

import org.exist.util.XMLUtil;
import java.util.Iterator;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

/**
 * Base class for all node set implementations returned by most
 * xpath expressions. It implements NodeList plus some additional
 * methods needed by the xpath engine.
 *
 * There are three classes extending NodeSet: NodeIDSet, ArraySet
 * and VirtualNodeSet. Depending on the context each of these
 * implementations has its advantages and drawbacks. ArraySet
 * uses a sorted array and binary search, while NodeIDSet is based
 * on a HashSet. VirtualNodeSet is specifically used for steps like
 * descendant::* etc..
 */
public abstract class NodeSet implements NodeList {

	public final static int ANCESTOR = 0;
	public final static int DESCENDANT = 1;
	
    public static NodeSet EMPTY_SET = new EmptyNodeSet();
    
    public abstract Iterator iterator();
    public abstract boolean contains(DocumentImpl doc, long nodeId);
    public abstract boolean contains(NodeProxy proxy);

    public boolean contains(DocumentImpl doc) {
	for(Iterator i = iterator(); i.hasNext(); )
	    if(((NodeProxy)i.next()).doc == doc)
		return true;
	return false;
    }

    public void add(DocumentImpl doc, long nodeId) {
	throw new RuntimeException("not implemented");
    }

    public void add(Node node) {
	throw new RuntimeException("not implemented");
    }

    public void add(NodeProxy proxy) {
	throw new RuntimeException("not implemented");
    }

    public void addAll(NodeList other) {
	throw new RuntimeException("not implemented");
    }

    public abstract void addAll(NodeSet other);

    public void remove(NodeProxy node) {
	throw new RuntimeException("not implemented");
    }

    public abstract int getLength();
    public abstract Node item(int pos);
    public abstract NodeProxy get(int pos);
    public abstract NodeProxy get(DocumentImpl doc, long nodeId);

    //public abstract int getLast();

    /**
     * Check if node has a parent contained in this node set.
     * If directParent is true, only direct ancestors are considered.
     */
    public boolean nodeHasParent(DocumentImpl doc, long gid,
                                 boolean directParent) {
        return nodeHasParent(doc, gid, directParent, false, -1);
    }

	public boolean nodeHasParent(NodeProxy p, boolean directParent) {
		return nodeHasParent(p.doc, p.gid, directParent, false); 
	}
		
	public boolean nodeHasParent(NodeProxy p, boolean directParent,
		boolean includeSelf) {
		return nodeHasParent(p.doc, p.gid, directParent, includeSelf); 
	}
	
    public boolean nodeHasParent(DocumentImpl doc, long gid,
                                 boolean directParent, boolean includeSelf) {
        return nodeHasParent(doc, gid, directParent, includeSelf, -1);
    }

    /**
     * Check if node has a parent contained in this node set.
     *
     * If directParent is true, only immediate ancestors are considered.
     * Otherwise the method will call itself recursively for the node's
     * parents.
     *
     * If includeSelf is true, the method returns also true if
     * the node itself is contained in the node set.
     */
    public boolean nodeHasParent(DocumentImpl doc, long gid,
                                 boolean directParent, boolean includeSelf, 
								 int level) {
        if(gid < 1)
            return false;
        if(includeSelf && contains(doc, gid))
            return true;
        if(level < 0)
            level = doc.getTreeLevel(gid);
        // calculate parent's gid
		long pid = XMLUtil.getParentId( doc, gid );
		includeSelf = false;
        if(contains(doc, pid))
            return true;
        else if(directParent)
            return false;
        else
            return nodeHasParent(doc, pid, directParent, includeSelf, level - 1);
    }

	public ArraySet getChildren(NodeSet al, int mode) {
		NodeProxy n, p;
		ArraySet result = new ArraySet(getLength());
		switch(mode) {
			case DESCENDANT:
				for(Iterator i = iterator(); i.hasNext(); ) {
					n = (NodeProxy)i.next();
					if(al.nodeHasParent(n, true, false))
						result.add(n);
				}
				break;
			case ANCESTOR:
				for(Iterator i = iterator(); i.hasNext(); ) {
					n = (NodeProxy)i.next();
					p = al.parentWithChild(n.doc, n.gid, true);
					if( p != null )
						result.add(p);
				}
				break;
		}
		return result;
	}

    /**
     * Search for a node contained in this node set, which is an
     * ancestor of the argument node.
     * If directParent is true, only immediate ancestors are considered.
     */
    public NodeProxy parentWithChild(DocumentImpl doc, long gid,
				     boolean directParent) {
        return parentWithChild(doc, gid, directParent, false, -1);
    }

    public NodeProxy parentWithChild(DocumentImpl doc, long gid,
				     boolean directParent, boolean includeSelf) {
        return parentWithChild(doc, gid, directParent, includeSelf, -1);
    }

    /**
     * Search for a node contained in this node set, which is an
     * ancestor of the argument node.
     * If directParent is true, only immediate ancestors are considered.
     * If includeSelf is true, the method returns true even if
     * the node itself is contained in the node set.
     */
    protected NodeProxy parentWithChild(DocumentImpl doc, long gid, boolean directParent,
					boolean includeSelf, int level) {
        if(gid < 1)
            return null;
        if(includeSelf && contains(doc, gid))
            return get(doc, gid);
        if(level < 0)
            level = doc.getTreeLevel(gid);
        // calculate parent's gid
        long pid = XMLUtil.getParentId(doc, gid);
        if(contains(doc, pid))
            return get(doc, pid);
        else if(directParent)
            return null;
        else
            return parentWithChild(doc, pid, directParent, includeSelf, level - 1);
    }

    public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,
                                boolean includeSelf) {
        return parentWithChild(proxy.doc, proxy.gid, directParent, includeSelf, -1);
    }

    public NodeSet getParents() {
        ArraySet parents = new ArraySet(getLength());
        NodeProxy p;
        long pid;
        for(Iterator i = iterator(); i.hasNext(); ) {
            p = (NodeProxy)i.next();
            // calculate parent's gid
            pid = XMLUtil.getParentId(p.doc, p.gid);
            parents.add(new NodeProxy(p.doc, pid, Node.ELEMENT_NODE));
        }
        return parents;
    }

    public NodeSet intersection(NodeSet other) {
        long start = System.currentTimeMillis();
        NodeIDSet r = new NodeIDSet();
        NodeProxy l;
        for(Iterator i = iterator(); i.hasNext(); ) {
            l = (NodeProxy)i.next();
            if(other.contains(l))
                r.add(l);
        }
        for(Iterator i = other.iterator(); i.hasNext(); ) {
            l = (NodeProxy)i.next();
            if(contains(l) && (!r.contains(l)))
                r.add(l);
        }
        return r;
    }

    public NodeSet union(NodeSet other) {
        long start = System.currentTimeMillis();
        NodeIDSet result = new NodeIDSet();
        result.addAll(other);
        NodeProxy p;
        for(Iterator i = iterator(); i.hasNext(); ) {
            p = (NodeProxy)i.next();
            if(!result.contains(p))
                result.add(p);
        }
        return result;
    }

	public NodeSet subtract(NodeSet other) {
		long start = System.currentTimeMillis();
        NodeIDSet result = new NodeIDSet();
		NodeProxy p;
        for(Iterator i = iterator(); i.hasNext(); ) {
            p = (NodeProxy)i.next();
            if(!other.contains(p))
                result.add(p);
        }
        return result;
	}
}

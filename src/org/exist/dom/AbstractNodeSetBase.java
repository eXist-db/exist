/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
*  You should have received a copy of the GNU Lesser General Public License
*  along with this program; if not, write to the Free Software
*  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
* 
*  $Id$
*/
package org.exist.dom;

import java.util.Iterator;

import org.apache.log4j.Logger;

/**
 * @author wolf
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public abstract class AbstractNodeSetBase extends AbstractNodeSet {

	protected final static Logger LOG = Logger.getLogger(AbstractNodeSetBase.class);
	
	private final static int UNKNOWN = -1;
	private final static int NOT_INDEXED = 0;
	private final static int ALL_NODES_IN_INDEX = 1;
	
	// indicates if the nodes in this set and their descendant nodes
	// have been fulltext indexed
	private int hasIndex = UNKNOWN;
	
	private boolean isCached = false;
	
	protected AbstractNodeSetBase() {
		super();
	}

	public void setIsCached(boolean cached) {
		isCached = cached;
	}
	
	public boolean isCached() {
		return isCached;
	}
	
	/**
	 * Returns true if all nodes in this node set and their descendants
	 * are included in the fulltext index. This information is required
	 * to determine if comparison operators can use the
	 * fulltext index to speed up equality comparisons.
	 * 
	 * @see org.exist.xquery.GeneralComparison
	 * @see org.exist.xquery.ValueComparison
	 * @return
	 */
	public boolean hasIndex() {
		if(hasIndex == UNKNOWN) {
			hasIndex = ALL_NODES_IN_INDEX;
			for (Iterator i = iterator(); i.hasNext();) {
				if (!((NodeProxy) i.next()).hasIndex()) {
					hasIndex = NOT_INDEXED;
					break;
				}
			}
		}
		return hasIndex == ALL_NODES_IN_INDEX;
	}
}

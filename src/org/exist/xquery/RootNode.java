/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 *  Represents the document-root node in an expression.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    02 August 2002
 */
public class RootNode extends Step {

	/**  Constructor for the RootNode object */
	public RootNode(XQueryContext context) {
		super(context, Constants.SELF_AXIS);
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem) {
		DocumentSet ds = context.getStaticallyKnownDocuments();
		if(ds == null || ds.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		//NodeSet result = new ExtArrayNodeSet(ds.getLength(), 1);
		NodeSet result = new ArraySet(ds.getLength());
		for (Iterator i = ds.iterator(); i.hasNext();) {
			result.add(new NodeProxy((DocumentImpl) i.next(), -1));
		}
		return result;
	}

	public String pprint() {
		return "ROOT";
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Step#returnsType()
	 */
	public int returnsType() {
		return Type.NODE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Step#resetState()
	 */
	public void resetState() {
	}
}

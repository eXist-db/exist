/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.memtree;

import org.w3c.dom.DOMException;
import org.w3c.dom.ProcessingInstruction;

public class ProcessingInstructionImpl
	extends NodeImpl
	implements ProcessingInstruction {

	/**
	 * @param doc
	 * @param nodeNumber
	 */
	public ProcessingInstructionImpl(DocumentImpl doc, int nodeNumber) {
		super(doc, nodeNumber);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.ProcessingInstruction#getTarget()
	 */
	public String getTarget() {
		return document.nodeName[nodeNumber].getLocalName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.ProcessingInstruction#getData()
	 */
	public String getData() {
		return new String(document.characters, document.alpha[nodeNumber],
		document.alphaLen[nodeNumber]);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.ProcessingInstruction#setData(java.lang.String)
	 */
	public void setData(String arg0) throws DOMException {
	}

}

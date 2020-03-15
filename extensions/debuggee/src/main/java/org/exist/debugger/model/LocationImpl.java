/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.debugger.model;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class LocationImpl implements Location {

	private String fileURI;

	private int beginColumn;
	private int beginLine;
//	private int endColumn;
//	private int endLine;
	private int level;

	public LocationImpl(Node node) {
		NamedNodeMap attrs = node.getAttributes();
		
		for (int i = 0; i < attrs.getLength(); i++) {
			Node attr = attrs.item(i);
			
			if (attr.getNodeName().equals("lineno")) {
				beginLine = Integer.parseInt(attr.getNodeValue());
			
			} else if (attr.getNodeName().equals("filename")) {
				fileURI = attr.getNodeValue();
			
			} else if (attr.getNodeName().equals("level")) {
				level = Integer.parseInt(attr.getNodeValue());
			
			} else if (attr.getNodeName().equals("cmdbegin")) {
				String[] begin = attr.getNodeValue().split(":");
				
				beginColumn = Integer.parseInt(begin[1]);
			
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.model.Location#getColumnBegin()
	 */
	public int getColumnBegin() {
		return beginColumn;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.model.Location#getFileURI()
	 */
	public String getFileURI() {
		return fileURI;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.model.Location#getLineBegin()
	 */
	public int getLineBegin() {
		return beginLine;
	}

	public int getLevel() {
		return level;
	}

	public String toString() {
		return ""+beginLine+":"+beginColumn+"@"+fileURI;
	}
}

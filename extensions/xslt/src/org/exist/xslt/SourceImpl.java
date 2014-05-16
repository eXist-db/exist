/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xslt;

import javax.xml.transform.Source;

import org.w3c.dom.Document;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SourceImpl implements Source {

	public static final int NOT_DEFAINED = -1;
	public static final int STRING = 1;
	public static final int DOM = 2;
	public static final int EXIST_Sequence = 3;

	String systemId = null;
	
	Object source;
	
	int type = NOT_DEFAINED;
	
	
	public SourceImpl(Document source) {
		this.source = source;
		type = DOM;
	}
	
	public SourceImpl(org.exist.source.Source source) {
		this.source = source;
		type = EXIST_Sequence;
	}

	public SourceImpl(String source) {
		this.source = source;
		type = STRING;
	}

	public int getType() {
		return type;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Source#getSystemId()
	 */
	public String getSystemId() {
		return systemId;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.Source#setSystemId(java.lang.String)
	 */
	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}

	public Object getSource() {
		return source;
	}

	public String toString() {
		if (type == STRING) {
			return (String)source;
		}
		return super.toString();
	}
}

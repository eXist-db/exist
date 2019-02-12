/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
package org.exist.debugger.model;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class VariableImpl implements Variable {
	
	private String name;
	private String value;
	private String type;
	
	private NodeList complex_value = null;
	
	public VariableImpl(String name, String value, String type) {
		this.name = name;
		this.value = value;
		this.type = type;
	}

	public VariableImpl(Node node) {
		NamedNodeMap attrs = node.getAttributes();
		
		for (int i = 0; i < attrs.getLength(); i++) {
			Node attr = attrs.item(i);
			
			if (attr.getNodeName().equals("name")) {
				name = attr.getNodeValue();
			} else if (attr.getNodeName().equals("type")) {
				type = attr.getNodeValue();
			}
		}
		
		//XXX: how should xml be processed???
		complex_value = node.getChildNodes();
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.model.Variable#getName()
	 */
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see org.exist.debugger.model.Variable#getValue()
	 */
	public String getValue() {
		if (complex_value == null)
			return value;
		
		if ((complex_value.getLength() == 1) && (complex_value.item(0).getNodeType() == Node.TEXT_NODE))
			return ((Text)complex_value.item(0)).getData();
		
		//TODO: xml?
		return complex_value.toString();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.debugger.model.Variable#getType()
	 */
	public String getType() {
		return type;
	}


	public String toString() {
		return ""+getName()+" = "+getValue();
	}
}

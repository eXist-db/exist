/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

public class CommentImpl extends NodeImpl implements Comment {

	/**
	 * @param doc
	 * @param nodeNumber
	 */
	public CommentImpl(DocumentImpl doc, int nodeNumber) {
		super(doc, nodeNumber);
	}

    public boolean hasChildNodes() {
       return false;
    }

    public Node getFirstChild() {
        return null;
    }
    
    public String getStringValue() {
    	return getData();
    }
    
	public String getLocalName() {		
        return "";
	}   
	
	public String getNamespaceURI() {
        return "";
	}		

	/* (non-Javadoc)
	 * @see org.w3c.dom.CharacterData#getData()
	 */
	public String getData() throws DOMException {
		return new String(document.characters, document.alpha[nodeNumber],
			document.alphaLen[nodeNumber]);
	}
	
	public AtomicValue atomize() throws XPathException {
		return new StringValue(getData());
	}	
	
    public int getLength() {
    	return getData().length();
    }

	/* (non-Javadoc)
	 * @see org.w3c.dom.CharacterData#setData(java.lang.String)
	 */
	public void setData(String arg0) throws DOMException {
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.CharacterData#substringData(int, int)
	 */
	public String substringData(int arg0, int arg1) throws DOMException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.CharacterData#appendData(java.lang.String)
	 */
	public void appendData(String arg0) throws DOMException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.CharacterData#insertData(int, java.lang.String)
	 */
	public void insertData(int arg0, String arg1) throws DOMException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.CharacterData#deleteData(int, int)
	 */
	public void deleteData(int arg0, int arg1) throws DOMException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.CharacterData#replaceData(int, int, java.lang.String)
	 */
	public void replaceData(int arg0, int arg1, String arg2) throws DOMException {
		// TODO Auto-generated method stub

	}
	
	public int getItemType() {
		return Type.COMMENT;
	}    

	public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("in-memory#");
    	result.append("comment {");      	
    	result.append(getData());        
    	result.append("} ");    	
    	return result.toString();
    }	

}

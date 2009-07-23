/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.memtree;

import org.exist.dom.QName;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
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
		QName qn = (QName)document.namePool.get(document.nodeName[nodeNumber]);
		return qn != null ? qn.getLocalName() : null;
	}
	
    public String getStringValue() {
        // TODO: this could be optimized
    	return getData().replaceFirst("^\\s+","");
    }	
    
	public String getLocalName() {
        return getTarget();
	}    
	
	public String getNamespaceURI() {
        return "";
	}	

	/* (non-Javadoc)
	 * @see org.w3c.dom.ProcessingInstruction#getData()
	 */
	public String getData() {
		return new String(document.characters, document.alpha[nodeNumber],
		document.alphaLen[nodeNumber]);
	}
	
	public AtomicValue atomize() throws XPathException {
		return new StringValue(getData());
	}	

	/* (non-Javadoc)
	 * @see org.w3c.dom.ProcessingInstruction#setData(java.lang.String)
	 */
	public void setData(String arg0) throws DOMException {
	}
	
    /** ? @see org.w3c.dom.Node#getBaseURI()
	 */
    public String getBaseURI() {
        String baseURI = "";
        int parent = -1;
        int test = -1;
        test = document.getParentNodeFor(nodeNumber);

        if (document.nodeKind[test] != Node.DOCUMENT_NODE) {
            parent = test;
        } 
        // fixme! Testa med 0/ljo
        while (parent != -1 && document.getNode(parent).getBaseURI() != null) {
            if ("".equals(baseURI)) {
                baseURI = document.getNode(parent).getBaseURI();
            } else {
                baseURI = document.getNode(parent).getBaseURI() + "/" + baseURI;
            }

            test = document.getParentNodeFor(parent);
            if (document.nodeKind[test] == Node.DOCUMENT_NODE) {
                return baseURI;
            } else {
                parent = test;
            }
        }
        if ("".equals(baseURI)) {
            baseURI = getDocument().getBaseURI();
        }
        return baseURI;
    }

	public Node getFirstChild() {
		//No child
		return null;
	}	
	
	public int getItemType() {
		return Type.PROCESSING_INSTRUCTION;
	}   
	
	public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("in-memory#");
    	result.append("processing-instruction {");
    	result.append(getTarget());
    	result.append("} {");        
    	result.append(getData());
    	result.append("} ");    	
    	return result.toString();
    }  

}

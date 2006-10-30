
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 * 
 */
package org.exist.dom;

import org.exist.storage.Signatures;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.numbering.NodeId;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;


/**
 * TextImpl.java
 * 
 * @author wolf
 *
 */
public class TextImpl extends CharacterDataImpl implements Text {

    public TextImpl() {
        super( Node.TEXT_NODE );
    }

    public TextImpl( String data ) {
        super( Node.TEXT_NODE, data );
    }

    public TextImpl( NodeId nodeId, String data ) {
        super( Node.TEXT_NODE, nodeId, data );
    }
    
    public static StoredNode deserialize(byte[] data,
                                       int start,
                                       int len,
                                       DocumentImpl doc,
                                       boolean pooled) {
        TextImpl text;
        if(pooled)
            text = (TextImpl)NodeObjectPool.getInstance().borrowNode(TextImpl.class);
        else
            text = new TextImpl();
        int dlnLen = ByteConversion.byteToShort(data, start + 1);
        NodeId dln =
                doc.getBroker().getBrokerPool().getNodeFactory().createFromData(dlnLen, data, start + 3);
        text.setNodeId(dln);
        int nodeIdLen = dln.size();
        text.cdata = UTF8.decode(data, start + nodeIdLen + 3, len - nodeIdLen - 3);
        return text;
    }

    public void appendData( String arg ) throws DOMException {
        super.appendData( arg );
    }

    public void appendData( char[] data, int start, int howmany ) throws DOMException {
        super.appendData( data, start, howmany );
    }

    public void deleteData( int offset, int count ) throws DOMException {
        super.deleteData( offset, count );
    }
    
    public int getLength() {
        return super.getLength();
    }

    public String getNodeValue() {
        return super.getNodeValue();
    }

    public void insertData( int offset, String arg ) throws DOMException {
        super.insertData( offset, arg );
    }

    public void replaceData( int offset, int count, String arg ) throws DOMException {
        super.replaceData( offset, count, arg );
    }

    public byte[] serialize() {
        final int nodeIdLen = nodeId.size();
        byte[] data = ByteArrayPool.getByteArray(cdata.UTF8Size() + nodeIdLen + 3);
        data[0] = (byte) ( Signatures.Char << 0x5 );
        ByteConversion.shortToByte((short) nodeId.units(), data, 1);
        nodeId.serialize(data, 3);
        cdata.UTF8Encode(data, nodeIdLen + 3);
        return data;
    }

    public void setNodeValue( String value ) throws DOMException {
        super.setNodeValue( value );
    }

    public Text splitText( int offset ) throws DOMException {
        return null;
    }

    public String substringData( int offset, int count ) throws DOMException {
        return super.substringData( offset, count );
    }

    public String toString( boolean top ) {
        if ( top ) {
            StringBuffer result = new StringBuffer();
            result.append( "<exist:text " );
            result.append( "xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" " );
            result.append( "exist:id=\"" );
            result.append( getNodeId() );
            result.append( "\" exist:source=\"" );
            result.append( ((DocumentImpl)getOwnerDocument()).getFileURI() );
            result.append( "\">" );
            result.append( getData() );
            result.append( "</exist:text>" );
            return result.toString();
        }
        else
            return toString();
    }

    public String toString() {
        return super.toString();
    }
    
    public int getChildCount() {
    	return 0;
    }
    
    public boolean hasChildNodes() {
        return false;        
    }
    
    public Node getFirstChild() {   
        //bad implementations don't call hasChildNodes before
        return null;
    }    

	/** ? @see org.w3c.dom.Text#isElementContentWhitespace()
	 */
	public boolean isElementContentWhitespace() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Text#getWholeText()
	 */
	public String getWholeText() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Text#replaceWholeText(java.lang.String)
	 */
	public Text replaceWholeText(String content) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return 0;
	}

	/** ? @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}
}


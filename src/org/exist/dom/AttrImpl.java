
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang Meier (wolfgang@exist-db.org)
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
 */
package org.exist.dom;

import org.exist.Namespaces;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.Signatures;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.util.XMLString;
import org.exist.util.pool.NodePool;
import org.exist.util.serializer.AttrList;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;

import java.io.UnsupportedEncodingException;

public class AttrImpl extends NamedNode implements Attr {
	
	public static final int LENGTH_NS_ID = 2; //sizeof short
	public static final int LENGTH_PREFIX_LENGTH = 2; //sizeof short
	
    public final static int CDATA = 0;
    public final static int ID = 1;
    public final static int IDREF = 2;
    public final static int IDREFS = 3;
    
    public final static int DEFAULT_ATTRIBUTE_TYPE = CDATA;
	
	protected int attributeType = DEFAULT_ATTRIBUTE_TYPE;
    protected XMLString value = null;

    public AttrImpl() {
    	super(Node.ATTRIBUTE_NODE);
    }

    public AttrImpl (QName name) {
        super( Node.ATTRIBUTE_NODE, name);
    }

    public AttrImpl( QName name, XMLString value ) {
        super( Node.ATTRIBUTE_NODE, name);
		this.value = value;
    }

    public AttrImpl (QName name, String str) {
        super( Node.ATTRIBUTE_NODE, name);
        this.value = new XMLString( str.toCharArray() );
    }

    public AttrImpl(AttrImpl other) {
        super(other);
        this.attributeType = other.attributeType;
        this.value = other.value;
    }
    
    public void clear() {
        super.clear();
        attributeType = DEFAULT_ATTRIBUTE_TYPE;
        value = null;
    }    
    
    public byte[] serialize() {
        if(nodeName.getLocalName() == null)
            throw new RuntimeException("Local name is null");
        final short id = ownerDocument.getBrokerPool().getSymbols().getSymbol( this );
        final byte idSizeType = Signatures.getSizeType( id );
        int prefixLen = 0;
        if (nodeName.needsNamespaceDecl()) {
            if (nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0)
            	prefixLen = UTF8.encoded(nodeName.getPrefix());
        }
        final int nodeIdLen = nodeId.size();
        final byte[] data = ByteArrayPool.getByteArray(
        		LENGTH_SIGNATURE_LENGTH + NodeId.LENGTH_NODE_ID_UNITS + nodeIdLen +
                Signatures.getLength(idSizeType) +
                (nodeName.needsNamespaceDecl() ? LENGTH_NS_ID + LENGTH_PREFIX_LENGTH + prefixLen : 0) + 
        		value.UTF8Size());
        int pos = 0;
        data[pos] = (byte) ( Signatures.Attr << 0x5 );
        data[pos] |= idSizeType;
        data[pos] |= (byte) (attributeType << 0x2);
        if(nodeName.needsNamespaceDecl())
            data[pos] |= 0x10;        
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;        
        ByteConversion.shortToByte((short) nodeId.units(), data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId.serialize(data, pos);
        pos += nodeIdLen;        
        Signatures.write(idSizeType, id, data, pos);
        pos += Signatures.getLength(idSizeType);
        if(nodeName.needsNamespaceDecl()) {
            final short nsId = ownerDocument.getBrokerPool().getSymbols().getNSSymbol(nodeName.getNamespaceURI());
            ByteConversion.shortToByte(nsId, data, pos);
            pos += LENGTH_NS_ID;
            ByteConversion.shortToByte((short)prefixLen, data, pos);
            pos += LENGTH_PREFIX_LENGTH;
            if (nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0)
                UTF8.encode(nodeName.getPrefix(), data, pos);
            pos += prefixLen;
        }
        value.UTF8Encode(data, pos);
        return data;
    }
    
    public static StoredNode deserialize( byte[] data, int start, int len, DocumentImpl doc, boolean pooled ) {
        int pos = start;
        byte idSizeType = (byte) ( data[pos] & 0x3 );
		boolean hasNamespace = (data[pos] & 0x10) == 0x10;
        int attrType = ( data[pos] & 0x4 ) >> 0x2;
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        pos += dln.size();
        short id = (short) Signatures.read(idSizeType, data, pos);
		pos += Signatures.getLength(idSizeType);
        String name = doc.getBrokerPool().getSymbols().getName(id);
        if (name == null)
            throw new RuntimeException("no symbol for id " + id);
        short nsId = 0;
        String prefix = null;
		if (hasNamespace) {
			nsId = ByteConversion.byteToShort(data, pos);
			pos += LENGTH_NS_ID;
			int prefixLen = ByteConversion.byteToShort(data, pos);
			pos += LENGTH_PREFIX_LENGTH;
			if (prefixLen > 0)
				prefix = UTF8.decode(data, pos, prefixLen).toString();
			pos += prefixLen;
		}
		String namespace = nsId == 0 ? "" : doc.getBrokerPool().getSymbols().getNamespace(nsId);
        XMLString value = UTF8.decode(data, pos, len - (pos - start));

        //OK : we have the necessary material to build the attribute
        AttrImpl attr;
        if(pooled)
            attr = (AttrImpl) NodePool.getInstance().borrowNode(Node.ATTRIBUTE_NODE);
//            attr = (AttrImpl)NodeObjectPool.getInstance().borrowNode(AttrImpl.class);
        else
            attr = new AttrImpl();
        attr.setNodeName(doc.getBrokerPool().getSymbols().getQName(Node.ATTRIBUTE_NODE, namespace, name, prefix));
        attr.value = value;
        attr.setNodeId(dln);
        if (dln == null)
        	throw new RuntimeException("no node id " + id);
        attr.setType(attrType);
        return attr;
    }

    public static void addToList(DBBroker broker, byte[] data, int start, int len, AttrList list) {
        int pos = start;
        byte idSizeType = (byte) ( data[pos] & 0x3 );
        boolean hasNamespace = (data[pos] & 0x10) == 0x10;
        int attrType = ( data[pos] & 0x4 ) >> 0x2;
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        NodeId dln = broker.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        pos += dln.size();
        short id = (short) Signatures.read(idSizeType, data, pos);
        pos += Signatures.getLength(idSizeType);
        String name = broker.getBrokerPool().getSymbols().getName(id);
        if (name == null)
            throw new RuntimeException("no symbol for id " + id);
        short nsId = 0;
        String prefix = null;
        if (hasNamespace) {
            nsId = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_PREFIX_LENGTH;
            if (prefixLen > 0)
                prefix = UTF8.decode(data, pos, prefixLen).toString();
            pos += prefixLen;
        }
        String namespace = nsId == 0 ? "" : broker.getBrokerPool().getSymbols().getNamespace(nsId);
        String value;
        try {
            value = new String( data, pos, len - (pos - start), "UTF-8" );
        } catch (UnsupportedEncodingException uee) {
            LOG.warn(uee);
            value = new String( data, pos, len - (pos - start));
        }
        list.addAttribute(broker.getBrokerPool().getSymbols().getQName(Node.ATTRIBUTE_NODE, namespace, name, prefix), value, attrType, dln);
    }

    public String getName() {
        return nodeName.getStringValue();
    }

	public int getType() {
		return attributeType;
	}
	
	public void setType(int type) {
        //TODO : range check -pb
		attributeType = type;
	}

    public static String getAttributeType(int type) {
        if (type == AttrImpl.ID)
            return "ID";
        if (type == AttrImpl.IDREF)
            return "IDREF";
        if (type == AttrImpl.IDREFS)
            return "IDREFS";
        return "CDATA";
    }

    public String getValue() {
        return value.toString();
    }
    
    public String getNodeValue() {
        return value.toString();
    }    
    
    public void setValue(String str) throws DOMException {
        this.value = new XMLString(str.toCharArray());
    }

    public Element getOwnerElement() {
        return (Element) ((DocumentImpl)getOwnerDocument()).getNode( nodeId.getParentId() );
    }

    public boolean getSpecified() {
        return true;
    } 

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append( ' ' );
        buf.append( nodeName );
        buf.append( "=\"" );
        buf.append( value );
        buf.append( '"' );
        return buf.toString();
    }

    public String toString( boolean top ) {
        if ( top ) {
            StringBuilder result = new StringBuilder();
            result.append( "<exist:attribute " );
            result.append( "xmlns:exist=\"" + Namespaces.EXIST_NS + "\" " );
            result.append( "exist:id=\"" );
            result.append( getNodeId() );
            result.append( "\" exist:source=\"" );
            result.append( ((DocumentImpl)getOwnerDocument()).getFileURI());
            result.append( "\" " );
            result.append( getNodeName() );
            result.append( "=\"" );
            result.append( getValue() );
            result.append( "\"/>" );
            return result.toString();
        }
        else
            return toString();
    }
    
    public boolean hasChildNodes() {
        return false;        
    } 
    
    public int getChildCount() {
    	return 0;
    }
    
    public Node getFirstChild() {   
        //bad implementations don't call hasChildNodes before
        return null;
    }        

	/** ? @see org.w3c.dom.Attr#getSchemaTypeInfo()
	 */
	public TypeInfo getSchemaTypeInfo() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Attr#isId()
	 */
	public boolean isId() {
		return this.getType() == ID;
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


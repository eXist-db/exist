
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
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
 *  $Id:
 */
package org.exist.dom;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.exist.storage.Signatures;
import org.exist.util.ByteArrayPool;
import org.exist.util.UTF8;
import org.exist.util.XMLUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    9. Juli 2002
 */
public class AttrImpl extends NodeImpl implements Attr {
	
	public final static int CDATA = 0;
	public final static int ID = 1;
	
	protected int attributeType = CDATA;
    protected ElementImpl ownerElement = null;
    protected String value = null;


    /**
     *  Constructor for the AttrImpl object
     *
     *@param  gid  Description of the Parameter
     */
    public AttrImpl( long gid ) {
        super( Node.ATTRIBUTE_NODE, gid );
    }


    /**
     *  Constructor for the AttrImpl object
     *
     *@param  name   Description of the Parameter
     *@param  value  Description of the Parameter
     */
    public AttrImpl( String name, String value ) {
        super( Node.ATTRIBUTE_NODE, name );
        //this.value = XMLUtil.encodeAttrMarkup( value );
		this.value = value;
    }


    /**
     *  Description of the Method
     *
     *@param  data  Description of the Parameter
     *@param  doc   Description of the Parameter
     *@return       Description of the Return Value
     */
    public static NodeImpl deserialize( byte[] data, int start, int len, DocumentImpl doc ) {
        byte idSizeType = (byte) ( data[start] & 0x3 );
        short id = (short) Signatures.read( idSizeType, data, start + 1 );
        int attrType = (int)( ( data[start] & 0x4 ) >> 0x2);
        String name = doc.getSymbols().getName( id );
        String value;
        try {
            value =
                new String( data, start + 1 + Signatures.getLength( idSizeType ),
                len - 1 - Signatures.getLength( idSizeType ),
                "UTF-8" );
        } catch ( UnsupportedEncodingException uee ) {
            value =
                new String( data, start + 1 + idSizeType,
                len - 1 - idSizeType );
        }
        AttrImpl attr = new AttrImpl( name, value );
        attr.setType( attrType );
        return attr;
    }


    /**
     *  Gets the name attribute of the AttrImpl object
     *
     *@return    The name value
     */
    public String getName() {
        return nodeName;
    }


	public int getType() {
		return attributeType;
	}
	
	public void setType(int type) {
		attributeType = type;
	}
	
    /**
     *  Gets the nodeValue attribute of the AttrImpl object
     *
     *@return    The nodeValue value
     */
    public String getNodeValue() {
        return value;
    }


    /**
     *  Gets the ownerElement attribute of the AttrImpl object
     *
     *@return    The ownerElement value
     */
    public Element getOwnerElement() {
        return (Element) ownerDocument.getNode( getParentGID() );
    }


    /**
     *  Gets the specified attribute of the AttrImpl object
     *
     *@return    The specified value
     */
    public boolean getSpecified() {
        return true;
    }


    /**
     *  Gets the value attribute of the AttrImpl object
     *
     *@return    The value value
     */
    public String getValue() {
        return value;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public byte[] serialize() {
        final short id = ownerDocument.getSymbols().getSymbol( this );
        final byte idSizeType = Signatures.getSizeType( id );
        final byte[] data = ByteArrayPool.getByteArray(UTF8.encoded(value) +
            Signatures.getLength( idSizeType ) +
            1);
        data[0] = (byte) ( Signatures.Attr << 0x5 );
        data[0] |= idSizeType;
        data[0] |= (byte) (attributeType << 0x2);
        Signatures.write( idSizeType, id, data, 1 );
        UTF8.encode(value, data, 1 + Signatures.getLength( idSizeType ));
        return data;
    }


    /**
     *  Sets the value attribute of the AttrImpl object
     *
     *@param  value             The new value value
     *@exception  DOMException  Description of the Exception
     */
    public void setValue( String value ) throws DOMException {
        this.value = XMLUtil.encodeAttrMarkup( value );
    }


    /**
     *  Description of the Method
     *
     *@param  contentHandler    Description of the Parameter
     *@param  lexicalHandler    Description of the Parameter
     *@param  first             Description of the Parameter
     *@param  prefixes          Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void toSAX( ContentHandler contentHandler,
                       LexicalHandler lexicalHandler, boolean first,
                       ArrayList prefixes )
         throws SAXException {
        if ( first ) {
            AttributesImpl attribs = new AttributesImpl();
            attribs.addAttribute( "http://exist.sourceforge.net/NS/exist", "id",
                "exist:id", "CDATA", Long.toString( gid ) );
            attribs.addAttribute( "http://exist.sourceforge.net/NS/exist", "source",
                "exist:source", "CDATA", ownerDocument.getFileName() );
            attribs.addAttribute( getNamespaceURI(), getLocalName(),
                getNodeName(), "CDATA", getValue() );
            contentHandler.startElement( "http://exist.sourceforge.net/NS/exist", "attribute",
                "exist:attribute", attribs );
            contentHandler.endElement( "http://exist.sourceforge.net/NS/exist", "attribute",
                "exist:attribute" );
        }
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( ' ' );
        buf.append( nodeName );
        buf.append( "=\"" );
        buf.append( value );
        buf.append( '"' );
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  top  Description of the Parameter
     *@return      Description of the Return Value
     */
    public String toString( boolean top ) {
        if ( top ) {
            StringBuffer result = new StringBuffer();
            result.append( "<exist:attribute " );
            result.append( "xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" " );
            result.append( "exist:id=\"" );
            result.append( gid );
            result.append( "\" exist:source=\"" );
            result.append( ownerDocument.getFileName() );
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
}



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
 *  $Id
 * 
 */
package org.exist.dom;

import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.DOMException;
import java.io.*;
import org.exist.util.*;
import org.exist.storage.*;


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
    
    /**
     *  Constructor for the TextImpl object
     *
     *@param  gid  Description of the Parameter
     */
    public TextImpl( long gid ) {
        super( Node.TEXT_NODE, gid );
    }


    /**
     *  Constructor for the TextImpl object
     *
     *@param  data  Description of the Parameter
     */
    public TextImpl( StringBuffer data ) {
        super( Node.TEXT_NODE, data );
    }


    /**
     *  Constructor for the TextImpl object
     *
     *@param  data  Description of the Parameter
     */
    public TextImpl( String data ) {
        super( Node.TEXT_NODE, data );
    }


    /**
     *  Constructor for the TextImpl object
     *
     *@param  gid   Description of the Parameter
     *@param  data  Description of the Parameter
     */
    public TextImpl( long gid, String data ) {
        super( Node.TEXT_NODE, gid, data );
    }


    /**
     *  Constructor for the TextImpl object
     *
     *@param  data     Description of the Parameter
     *@param  start    Description of the Parameter
     *@param  howmany  Description of the Parameter
     */
    public TextImpl( char[] data, int start, int howmany ) {
        super( Node.TEXT_NODE, data, start, howmany );
    }

    public static NodeImpl deserialize( byte[] data, int start, int len ) {
        final TextImpl text = new TextImpl( 0 );
        try {
            text.appendData(new String( data, start + 1, len - 1, "UTF-8" ));
        } catch ( UnsupportedEncodingException uee ) {
            text.appendData(new String( data, start + 1, len - 1 ));
        }
        return text;
    }


    /**
     *  Description of the Method
     *
     *@param  arg               Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void appendData( String arg ) throws DOMException {
        super.appendData( arg );
    }


    /**
     *  Description of the Method
     *
     *@param  data              Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  howmany           Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void appendData( char[] data, int start, int howmany ) throws DOMException {
        super.appendData( data, start, howmany );
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  count             Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void deleteData( int offset, int count ) throws DOMException {
        super.deleteData( offset, count );
    }
    
    /**
     *  Gets the length attribute of the TextImpl object
     *
     *@return    The length value
     */
    public int getLength() {
        return super.getLength();
    }


    /**
     *  Gets the nodeValue attribute of the TextImpl object
     *
     *@return    The nodeValue value
     */
    public String getNodeValue() {
        return super.getNodeValue();
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  arg               Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void insertData( int offset, String arg ) throws DOMException {
        super.insertData( offset, arg );
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  count             Description of the Parameter
     *@param  arg               Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void replaceData( int offset, int count, String arg ) throws DOMException {
        super.replaceData( offset, count, arg );
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public byte[] serialize() {
        final byte[] data = new byte[StringUtil.utflen(cdata) + 1];
        data[0] = (byte) ( Signatures.Char << 0x5 );
        StringUtil.utfwrite(data, 1, cdata);
        return data;
    }


    /**
     *  Sets the nodeValue attribute of the TextImpl object
     *
     *@param  value             The new nodeValue value
     *@exception  DOMException  Description of the Exception
     */
    public void setNodeValue( String value ) throws DOMException {
        super.setNodeValue( value );
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  DOMException  Description of the Exception
     */
    public Text splitText( int offset ) throws DOMException {
        return null;
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  count             Description of the Parameter
     *@return                   Description of the Return Value
     *@exception  DOMException  Description of the Exception
     */
    public String substringData( int offset, int count ) throws DOMException {
        return super.substringData( offset, count );
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
            result.append( "<exist:text " );
            result.append( "xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" " );
            result.append( "exist:id=\"" );
            result.append( gid );
            result.append( "\" exist:source=\"" );
            result.append( ownerDocument.getFileName() );
            result.append( "\">" );
            result.append( getData() );
            result.append( "</exist:text>" );
            return result.toString();
        }
        else
            return toString();
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return super.toString();
    }
}


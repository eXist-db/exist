
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
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
import org.exist.util.UTF8;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.Text;


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
    
    public TextImpl( long gid ) {
        super( Node.TEXT_NODE, gid );
    }

    public TextImpl( String data ) {
        super( Node.TEXT_NODE, data );
    }
    
    public TextImpl( long gid, String data ) {
        super( Node.TEXT_NODE, gid, data );
    }

    public TextImpl( char[] data, int start, int howmany ) {
        super( Node.TEXT_NODE, data, start, howmany );
    }

    public static NodeImpl deserialize( byte[] data, int start, int len, boolean pooled ) {
        TextImpl text;
        if(pooled)
            text = (TextImpl)NodeObjectPool.getInstance().borrowNode(TextImpl.class);
        else
            text = new TextImpl();
        text.cdata = UTF8.decode(data, start + 1, len - 1);
        /*try { 
            text.appendData(new String( data, start + 1, len - 1, "UTF-8" ));
        } catch ( UnsupportedEncodingException uee ) {
            text.appendData(new String( data, start + 1, len - 1 ));
        }*/
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
        byte[] data = ByteArrayPool.getByteArray(cdata.UTF8Size() + 1);
        data[0] = (byte) ( Signatures.Char << 0x5 );
        cdata.UTF8Encode(data, 1);
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

    public String toString() {
        return super.toString();
    }
}


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

import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import java.io.IOException;
import java.io.DataInput;
import java.io.DataOutput;
import java.util.ArrayList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.exist.util.*;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    27. Juni 2002
 */
public class CharacterDataImpl extends NodeImpl implements CharacterData {
    protected String cdata = null;


    public CharacterDataImpl( short nodeType ) {
        super( nodeType );
    }
    
    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  gid       Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, long gid ) {
        super( nodeType, "", gid );
    }


    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  gid       Description of the Parameter
     *@param  data      Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, long gid, String data ) {
        super( nodeType, "", gid );
        cdata = data;
    }


    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  gid       Description of the Parameter
     *@param  data      Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, long gid, StringBuffer data ) {
        super( nodeType, "", gid );
        this.cdata = data.toString();
    }


    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  data      Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, String data ) {
        super( nodeType, "" );
        cdata = data;
    }


    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  data      Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, StringBuffer data ) {
        super( nodeType, "" );
        cdata = data.toString();
    }


    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  data      Description of the Parameter
     *@param  start     Description of the Parameter
     *@param  howmany   Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, char[] data, int start, int howmany ) {
        super( nodeType, "" );
        cdata = new String(data, start, howmany);
    }

    public void clear() {
        super.clear();
        cdata = null;
    }

    /**
     *  Description of the Method
     *
     *@param  arg               Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void appendData( String arg ) throws DOMException {
        cdata = (cdata == null ? arg : cdata + arg);
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
        String s = new String( data, start, howmany );
        appendData( s );
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  count             Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void deleteData( int offset, int count ) throws DOMException {
        /*
         *  if(offset < 0 || offset + count > cdata.length())
         *  throw new DOMException(DOMException.INDEX_SIZE_ERR, "");
         */
        StringBuffer buf = new StringBuffer(cdata);
        buf.delete( offset, offset + count );
        cdata = buf.toString();
    }


    /**
     *  Gets the data attribute of the CharacterDataImpl object
     *
     *@return                   The data value
     *@exception  DOMException  Description of the Exception
     */
    public String getData() throws DOMException {
        return cdata;
    }


    /**
     *  Gets the length attribute of the CharacterDataImpl object
     *
     *@return    The length value
     */
    public int getLength() {
        return cdata.length();
    }


    /**
     *  Gets the nodeValue attribute of the CharacterDataImpl object
     *
     *@return    The nodeValue value
     */
    public String getNodeValue() {
        return cdata;
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  arg               Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void insertData( int offset, String arg ) throws DOMException {
        StringBuffer buf = new StringBuffer( cdata );
        buf.insert( offset, arg );
        cdata = buf.toString();
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
        /*
         *  if(offset < 0 || offset + count > cdata.length())
         *  throw new DOMException(DOMException.INDEX_SIZE_ERR, "");
         */
        cdata = 
            new StringBuffer( cdata ).replace( offset, offset + count, arg).toString();
    }


    /**
     *  Sets the data attribute of the CharacterDataImpl object
     *
     *@param  data              The new data value
     *@exception  DOMException  Description of the Exception
     */
    public void setData( String data ) throws DOMException {
        cdata = data;
    }


    /**
     *  Sets the data attribute of the CharacterDataImpl object
     *
     *@param  data              The new data value
     *@param  start             The new data value
     *@param  howmany           The new data value
     *@exception  DOMException  Description of the Exception
     */
    public void setData( char[] data, int start, int howmany ) throws DOMException {
        cdata = new String( data, start, howmany );
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
        return cdata.substring( offset, offset + count );
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
            contentHandler.startElement( "http://exist.sourceforge.net/NS/exist", "text",
                "exist:text", attribs );
        }
        char ch[] = new char[cdata.length()];
        cdata.getChars( 0, ch.length, ch, 0 );
        contentHandler.characters( ch, 0, ch.length );
        if ( first )
            contentHandler.endElement( "http://exist.sourceforge.net/NS/exist", "text",
                "exist:text" );

    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        return cdata;
    }
}


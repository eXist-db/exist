/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000,  Wolfgang Meier (wolfgang@exist-db.org)
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

import java.util.Set;

import org.exist.util.XMLString;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 *@created    27. Juni 2002
 */
public class CharacterDataImpl extends NodeImpl implements CharacterData {
    
    protected XMLString cdata = null;


    public CharacterDataImpl( short nodeType ) {
        super( nodeType, QName.TEXT_QNAME );
    }
    
    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  gid       Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, long gid ) {
        super( nodeType, QName.TEXT_QNAME, gid );
    }


    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  gid       Description of the Parameter
     *@param  data      Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, long gid, String data ) {
        super( nodeType, QName.TEXT_QNAME, gid );
        cdata = new XMLString(data.toCharArray());
    }

    /**
     *  Constructor for the CharacterDataImpl object
     *
     *@param  nodeType  Description of the Parameter
     *@param  data      Description of the Parameter
     */
    public CharacterDataImpl( short nodeType, String data ) {
        super( nodeType, QName.TEXT_QNAME );
		cdata = new XMLString(data.toCharArray());
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
        super( nodeType, QName.TEXT_QNAME );
        cdata = new XMLString(data, start, howmany);
    }

    public void clear() {
        cdata.reset();
    }

    /**
     *  Description of the Method
     *
     *@param  arg               Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void appendData( String arg ) throws DOMException {
    	if(cdata == null)
    		cdata = new XMLString(arg.toCharArray());
    	else
        	cdata.append(arg);
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
    	if(cdata == null)
    		cdata = new XMLString(data, start, howmany);
    	else
        	cdata.append( data, start, howmany );
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  count             Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void deleteData( int offset, int count ) throws DOMException {
    	if(cdata != null)
        	cdata.delete(offset, count);
    }


    /**
     *  Gets the data attribute of the CharacterDataImpl object
     *
     *@return                   The data value
     *@exception  DOMException  Description of the Exception
     */
    public String getData() throws DOMException {
    	if(cdata == null)
    		return null;
    	else
        	return cdata.toString();
    }
    
    public XMLString getXMLString() {
    	return cdata;
    }

    public String getLowerCaseData() throws DOMException {
    	if(cdata == null)
    		return null;
    	else
        	return cdata.toString().toLowerCase();
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
        return cdata.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  offset            Description of the Parameter
     *@param  arg               Description of the Parameter
     *@exception  DOMException  Description of the Exception
     */
    public void insertData( int offset, String arg ) throws DOMException {
    	if(cdata == null)
    		cdata = new XMLString(arg.toCharArray());
    	else
        	cdata.insert(offset, arg);
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
		if(cdata == null)
			throw new DOMException(DOMException.DOMSTRING_SIZE_ERR, 
				"string index out of bounds");
        cdata.replace(offset, count, arg);
    }


    /**
     *  Sets the data attribute of the CharacterDataImpl object
     *
     *@param  data              The new data value
     *@exception  DOMException  Description of the Exception
     */
    public void setData( String data ) throws DOMException {
        if(cdata == null)
        	cdata = new XMLString(data.toCharArray());
        else
        	cdata.setData(data.toCharArray(), 0, data.length());
    }

	public void setData( XMLString data ) throws DOMException {
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
        if(cdata == null)
        	cdata = new XMLString(data, start, howmany);
        else
        	cdata.setData(data, start, howmany);
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
    	if(cdata == null)
    		throw new DOMException(DOMException.DOMSTRING_SIZE_ERR, 
    			"string index out of bounds");
        return cdata.substring(offset, count);
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
                       Set namespaces)
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
        cdata.toString().getChars( 0, ch.length, ch, 0 );
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
    	if(cdata == null)
    		return null;
    	else
        	return cdata.toString();
    }
}


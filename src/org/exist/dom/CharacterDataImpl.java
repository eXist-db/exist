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

public class CharacterDataImpl extends NodeImpl implements CharacterData {
    
    protected XMLString cdata = null;
    
    public CharacterDataImpl( short nodeType ) {
        super( nodeType );
    }
    
    public CharacterDataImpl( short nodeType, long gid ) {
        super( nodeType, gid );
    }

    public CharacterDataImpl( short nodeType, long gid, String data ) {
        super( nodeType, gid );
        cdata = new XMLString(data.toCharArray());
    }

    public CharacterDataImpl( short nodeType, String data ) {
        super( nodeType );
		cdata = new XMLString(data.toCharArray());
    }

    public CharacterDataImpl( short nodeType, char[] data, int start, int howmany ) {
        super( nodeType );
        cdata = new XMLString(data, start, howmany);
    }

    public void clear() {
        cdata.reset();
    }

    public void appendData( String arg ) throws DOMException {
    	if(cdata == null)
    		cdata = new XMLString(arg.toCharArray());
    	else
        	cdata.append(arg);
    }

    public void appendData( char[] data, int start, int howmany ) throws DOMException {
    	if(cdata == null)
    		cdata = new XMLString(data, start, howmany);
    	else
        	cdata.append( data, start, howmany );
    }

    public void deleteData( int offset, int count ) throws DOMException {
    	if(cdata != null)
        	cdata.delete(offset, count);
    }

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
    
    public int getLength() {
        return cdata.length();
    }

    public String getNodeValue() {
        return cdata.toString();
    }

    public void insertData( int offset, String arg ) throws DOMException {
    	if(cdata == null)
    		cdata = new XMLString(arg.toCharArray());
    	else
        	cdata.insert(offset, arg);
    }

    public void replaceData( int offset, int count, String arg ) throws DOMException {
		if(cdata == null)
			throw new DOMException(DOMException.DOMSTRING_SIZE_ERR, 
				"string index out of bounds");
        cdata.replace(offset, count, arg);
    }

    public void setData( String data ) throws DOMException {
        if(cdata == null)
        	cdata = new XMLString(data.toCharArray());
        else
        	cdata.setData(data.toCharArray(), 0, data.length());
    }

	public void setData( XMLString data ) throws DOMException {
		cdata = data;
	}

    public void setData( char[] data, int start, int howmany ) throws DOMException {
        if(cdata == null)
        	cdata = new XMLString(data, start, howmany);
        else
        	cdata.setData(data, start, howmany);
    }

    public String substringData( int offset, int count ) throws DOMException {
    	if(cdata == null)
    		throw new DOMException(DOMException.DOMSTRING_SIZE_ERR, 
    			"string index out of bounds");
        return cdata.substring(offset, count);
    }

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

    public String toString() {
    	if(cdata == null)
    		return null;
    	else
        	return cdata.toString();
    }
    
    /**
     * Release all resources hold by this object.
     */
    public void release() {
    	cdata.release();
    	super.release();
    }
}
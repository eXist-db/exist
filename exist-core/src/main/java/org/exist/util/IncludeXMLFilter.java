/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *  Description of the Class
 *
 *@author     wolf
 *created    17. Juni 2002
 */
public class IncludeXMLFilter extends DefaultHandler {

    private ContentHandler handler;


    /**
     *  Constructor for the WhitespaceFilter object
     *
     *@param  handler  Description of the Parameter
     */
    public IncludeXMLFilter( ContentHandler handler ) {
        super();
        this.handler = handler;
    }

    public void characters( char ch[], int start, int length ) throws SAXException {
        handler.characters( ch, start, length );
    }

    public void endDocument() throws SAXException {
        // remove
    }

    public void endElement( String namespaceURI, String localName, String qName )
         throws SAXException {
        if ( localName == null || localName.length() == 0 )
            {localName = qName;}
        handler.endElement( namespaceURI, localName, qName );
    }

    public void endPrefixMapping( String prefix ) throws SAXException {
        handler.endPrefixMapping( prefix );
    }

    public void ignorableWhitespace( char[] ch, int start, int length )
         throws SAXException {
        handler.characters( ch, start, length );
    }

    public void processingInstruction( String target, String data )
         throws SAXException {
        handler.processingInstruction( target, data );
    }

    public void skippedEntity( String name ) throws SAXException {
        handler.skippedEntity( name );
    }

    public void startDocument() throws SAXException {
        // remove
    }

    public void startElement( String namespaceURI, String localName, String qName, Attributes atts )
         throws SAXException {
        if ( localName == null || localName.length() == 0 )
            {localName = qName;}
        handler.startElement( namespaceURI, localName, qName, atts );
    }

    public void startPrefixMapping( String prefix, String uri )
         throws SAXException {
        handler.startPrefixMapping( prefix, uri );
    }
}


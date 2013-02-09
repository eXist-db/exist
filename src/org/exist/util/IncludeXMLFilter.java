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


package org.exist.util;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/**
 *  Description of the Class
 *
 *@author     wolf
 *@created    17. Juni 2002
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


    /**
     *  Description of the Method
     *
     *@param  ch                Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  length            Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void characters( char ch[], int start, int length ) throws SAXException {
        handler.characters( ch, start, length );
    }


    /**
     *  Description of the Method
     *
     *@exception  SAXException  Description of the Exception
     */
    public void endDocument() throws SAXException {
        // remove
    }


    /**
     *  Description of the Method
     *
     *@param  namespaceURI      Description of the Parameter
     *@param  localName         Description of the Parameter
     *@param  qName             Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void endElement( String namespaceURI, String localName, String qName )
         throws SAXException {
        if ( localName == null || localName.length() == 0 )
            localName = qName;
        handler.endElement( namespaceURI, localName, qName );
    }


    /**
     *  Description of the Method
     *
     *@param  prefix            Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void endPrefixMapping( String prefix ) throws SAXException {
        handler.endPrefixMapping( prefix );
    }


    /**
     *  Description of the Method
     *
     *@param  ch                Description of the Parameter
     *@param  start             Description of the Parameter
     *@param  length            Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void ignorableWhitespace( char[] ch, int start, int length )
         throws SAXException {
        handler.characters( ch, start, length );
    }


    /**
     *  Description of the Method
     *
     *@param  target            Description of the Parameter
     *@param  data              Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void processingInstruction( String target, String data )
         throws SAXException {
        handler.processingInstruction( target, data );
    }


    /**
     *  Description of the Method
     *
     *@param  name              Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void skippedEntity( String name ) throws SAXException {
        handler.skippedEntity( name );
    }


    /**
     *  Description of the Method
     *
     *@exception  SAXException  Description of the Exception
     */
    public void startDocument() throws SAXException {
        // remove
    }


    /**
     *  Description of the Method
     *
     *@param  namespaceURI      Description of the Parameter
     *@param  localName         Description of the Parameter
     *@param  qName             Description of the Parameter
     *@param  atts              Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void startElement( String namespaceURI, String localName, String qName, Attributes atts )
         throws SAXException {
        if ( localName == null || localName.length() == 0 )
            localName = qName;
        handler.startElement( namespaceURI, localName, qName, atts );
    }


    /**
     *  Description of the Method
     *
     *@param  prefix            Description of the Parameter
     *@param  uri               Description of the Parameter
     *@exception  SAXException  Description of the Exception
     */
    public void startPrefixMapping( String prefix, String uri )
         throws SAXException {
        handler.startPrefixMapping( prefix, uri );
    }
}


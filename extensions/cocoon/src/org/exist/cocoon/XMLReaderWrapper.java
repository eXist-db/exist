package org.exist.cocoon;

import java.io.IOException;

import org.apache.excalibur.xml.sax.SAXParser;
import org.exist.Namespaces;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Wrapper around SAXParser interface from excalibur to
 * provide an (excalibur) independend implementation of XMLReader.
 */
public class XMLReaderWrapper implements XMLReader {
    private SAXParser saxParser;
    private ContentHandler contentHandler = null;
    private LexicalHandler lexicalHandler = null;
   
    /**
     * @param saxParser to wrap around
     */
    public XMLReaderWrapper(SAXParser saxParser) {
        this.saxParser = saxParser;
    }
    
    /**
     * @see org.xml.sax.XMLReader#parse(java.lang.String)
     */
    public void parse(String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#getFeature(java.lang.String)
     */
    public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        return false;
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#setFeature(java.lang.String, boolean)
     */
    public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
    }

    /**
     * @see org.xml.sax.XMLReader#getContentHandler()
     */
    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    /**
     * @see org.xml.sax.XMLReader#setContentHandler(org.xml.sax.ContentHandler)
     */
    public void setContentHandler(ContentHandler handler) {
        this.contentHandler = handler;
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#getDTDHandler()
     */
    public DTDHandler getDTDHandler() {
        return null;
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#setDTDHandler(org.xml.sax.DTDHandler)
     */
    public void setDTDHandler(DTDHandler handler) {
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#getEntityResolver()
     */
    public EntityResolver getEntityResolver() {
        return null;
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#setEntityResolver(org.xml.sax.EntityResolver)
     */
    public void setEntityResolver(EntityResolver resolver) {
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#getErrorHandler()
     */
    public ErrorHandler getErrorHandler() {
        return null;
    }

    /**
     * Not supported.
     * 
     * @see org.xml.sax.XMLReader#setErrorHandler(org.xml.sax.ErrorHandler)
     */
    public void setErrorHandler(ErrorHandler handler) {
    }

    /**
     * @see org.xml.sax.XMLReader#parse(org.xml.sax.InputSource)
     */
    public void parse(InputSource input) throws IOException, SAXException {
        if (lexicalHandler != null) {
            saxParser.parse(input, contentHandler, lexicalHandler);
        } else {
            saxParser.parse(input, contentHandler);
        }
    }

    /**
     * @see org.xml.sax.XMLReader#getProperty(java.lang.String)
     */
    public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Namespaces.SAX_LEXICAL_HANDLER)) {
            return lexicalHandler;
        } else {
            throw new SAXNotRecognizedException("Property " + name + " not recognized");
        }
    }

    /**
     * Only property <i>http://xml.org/sax/properties/lexical-handler</i> is 
     * supported.
     * 
     * @see org.xml.sax.XMLReader#setProperty(java.lang.String, java.lang.Object)
     */
    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(Namespaces.SAX_LEXICAL_HANDLER)) {
            if (!(value instanceof LexicalHandler)) {
                throw new SAXNotSupportedException("Value not of type LexicalHandler");
            }
            lexicalHandler = (LexicalHandler) value;
        } else {
            throw new SAXNotRecognizedException("Property " + name + " not recognized");
        }
    }
}

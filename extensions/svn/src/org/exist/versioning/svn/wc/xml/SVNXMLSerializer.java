/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.wc.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;


/**
 * This implementation of <b>ContentHandler</b> can write XML contents to 
 * a specified output stream or writer.  
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNXMLSerializer implements ContentHandler {

    private Writer myWriter;
    private String myEol = System.getProperty("line.separator");
    private boolean myCharacters = false;
    
    /**
     * Creates a serializer to write XML contents to the specified 
     * output stream.
     * 
     * @param os an output stream to write contents to
     */
    public SVNXMLSerializer(final OutputStream os) {
        try {
            myWriter = new OutputStreamWriter(os, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            myWriter = new OutputStreamWriter(os);
        }
    }
    
    /**
     * Creates a serializer to write XML contents to the specified 
     * writer.
     * 
     * @param writer a writer to write contents to
     */
    public SVNXMLSerializer(Writer writer) {
        myWriter = writer;
    }
    
    /**
     * Flushes written bytes.  
     * 
     * @throws IOException
     */
    public void flush() throws IOException {
        myWriter.flush();
    }

    /**
     * Starts xml document.
     * 
     * @throws SAXException 
     */
    public void startDocument() throws SAXException {
        try {
            myWriter.write("<?xml version=\"1.0\"?>");
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    /**
     * Starts an xml element.
     * 
     * @param namespaceURI 
     * @param localName 
     * @param qName 
     * @param atts 
     * @throws SAXException 
     */
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        try {
            myWriter.write(myEol);
            myWriter.write("<");
            myWriter.write(qName);
            for(int i = 0; i < atts.getLength(); i++) {
                myWriter.write(myEol);
                myWriter.write("   ");
                myWriter.write(atts.getQName(i));
                myWriter.write("=\"");
                myWriter.write(SVNEncodingUtil.xmlEncodeAttr(atts.getValue(i)));
                myWriter.write("\"");                    
            }
            if ("against".equals(qName)) {
                myWriter.write("/>");                
            } else {
                myWriter.write(">");
            }
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    /**
     * Handles CData characters.
     * 
     * @param ch 
     * @param start 
     * @param length 
     * @throws SAXException 
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
        myCharacters = true;
        try {
            String cdata = SVNEncodingUtil.xmlEncodeCDATA(new String(ch, start, length));
            myWriter.write(cdata);
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    /**
     * Closes the xml element. 
     * 
     * @param namespaceURI 
     * @param localName 
     * @param qName 
     * @throws SAXException 
     */
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        if ("against".equals(qName)) {
            return;
        }
        try {
            if (!myCharacters) {
                myWriter.write(myEol);
            }
            myWriter.write("</");
            myWriter.write(qName);
            myWriter.write(">");
        } catch (IOException e) {
            throw new SAXException(e);
        } finally {
            myCharacters = false;
        }
    }
    
    /**
     * Writes a End Of Line marker to the output.
     * 
     * @throws SAXException
     */
    public void endDocument() throws SAXException {
        try {
            myWriter.write(myEol);
        } catch (IOException e) {
            throw new SAXException(e);
        }
    }

    /**
     * Does nothing.
     * 
     * @param ch
     * @param start
     * @param length
     * @throws SAXException
     */
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    }
    
    /**
     * Does nothing.
     * 
     * @param prefix
     * @throws SAXException
     */
    public void endPrefixMapping(String prefix) throws SAXException {
    }
    
    /**
     * Does nothing.
     * 
     * @param name
     * @throws SAXException
     */
    public void skippedEntity(String name) throws SAXException {
    }
    
    /**
     * Does nothing.
     * 
     * @param locator
     */
    public void setDocumentLocator(Locator locator) {
    }
    
    /**
     * Does nothing.
     * 
     * @param target
     * @param data
     * @throws SAXException
     */
    public void processingInstruction(String target, String data) throws SAXException {
    }
    
    /**
     * Does nothing.
     * 
     * @param prefix
     * @param uri
     * @throws SAXException
     */
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

}

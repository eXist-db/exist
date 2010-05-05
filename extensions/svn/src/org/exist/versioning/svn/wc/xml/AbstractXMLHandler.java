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

import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;


/**
 * <b>AbstractXMLLogger</b> is a basic XML formatter for all 
 * XML handler classes which are provided in this package. All 
 * XML output is written to a specified <b>ContentHandler</b>.
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public abstract class AbstractXMLHandler implements Locator {
    
    private AttributesImpl mySharedAttributes;
    private ContentHandler myHandler;
    private ISVNDebugLog myLog;
    
    protected AbstractXMLHandler(ContentHandler contentHandler, ISVNDebugLog log) {
        myHandler = contentHandler;
        myLog = log == null ? SVNDebugLog.getDefaultLog() : log;
    }
    
    protected ISVNDebugLog getDebugLog() {
        return myLog;
    }
    
    /**
     * Starts logging. 
     *
     */
    public void startDocument() {
        try {
            getHandler().setDocumentLocator(this);
            getHandler().startDocument();
            openTag(getHeaderName());
        } catch (SAXException e) {
        }
    }
    
    /**
     * Stops logging.
     *
     */
    public void endDocument() {
        try {
            closeTag(getHeaderName());
            getHandler().endDocument();
        } catch (SAXException e) {
        }
    }
    
    private ContentHandler getHandler() {
        return myHandler;
    }
    
    protected abstract String getHeaderName();
    
    protected void openTag(String name) throws SAXException {
        if (mySharedAttributes == null) {
            mySharedAttributes = new AttributesImpl();
        }
        getHandler().startElement("", "", name, mySharedAttributes);
        mySharedAttributes.clear();
    }

    protected void closeTag(String name) throws SAXException {
        getHandler().endElement("", "", name);
    }
    
    protected void addTag(String tagName, String value) throws SAXException {
        if (mySharedAttributes == null) {
            mySharedAttributes = new AttributesImpl();
        }
        getHandler().startElement("", "", tagName, mySharedAttributes);
        mySharedAttributes.clear();
        value = value == null ? "" : value;
        getHandler().characters(value.toCharArray(), 0, value.length());
        getHandler().endElement("", "", tagName);
    }
    
    protected void addAttribute(String name, String value) {        
        if (mySharedAttributes == null) {
            mySharedAttributes = new AttributesImpl();
        }
        mySharedAttributes.addAttribute("", "", name, "CDATA", value);
    }

    /**
     * @return <code>0</code>
     */
    public int getColumnNumber() {
        return 0;
    }

    /**
     * @return <code>0</code>
     */
    public int getLineNumber() {
        return 0;
    }

    /**
     * @return <span class="javakeyword">null</span>
     */
    public String getPublicId() {
        return null;
    }

    /**
     * @return <span class="javakeyword">null</span>
     */
    public String getSystemId() {
        return null;
    }
}

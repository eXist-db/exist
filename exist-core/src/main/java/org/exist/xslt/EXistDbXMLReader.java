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
package org.exist.xslt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.storage.serializers.Serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * {@link org.xml.sax.XMLReader} that uses an {@link org.exist.storage.serializers.Serializer}
 * to send a database document to a content handler.
 *
 * @author <a href="mailto:Paul.L.Merchant.Jr@dartmouth.edu">Paul Merchant, Jr.</a>
 */
public class EXistDbXMLReader implements XMLReader, Locator {
    private final static Logger LOG = LogManager.getLogger(EXistDbSource.class);

    private ContentHandler contentHandler;
    private ErrorHandler   errHandler;
    
    private InputSource    source;
    
    public EXistDbXMLReader() {
    }
    
    @Override
    public ContentHandler getContentHandler() {
        return this.contentHandler;
    }
    
    @Override
    public DTDHandler getDTDHandler() {
        return null;
    }
    
    @Override
    public EntityResolver getEntityResolver() {
        return null;
    }
    
    @Override
    public ErrorHandler getErrorHandler() {
        return this.errHandler;
    }
    
    @Override
    public boolean getFeature(final String name) {
        return false;
    }
    
    @Override
    public Object getProperty(final String name) {
        return null;
    }
    
    @Override
    public void parse(final InputSource input) {
        if (!(input instanceof EXistDbInputSource)) {
            throw new UnsupportedOperationException("EXistDbXMLReader only accepts EXistDbInputSource");
        }

        EXistDbInputSource source = (EXistDbInputSource) input;
        try {
            final Serializer serializer = source.getBroker().newSerializer();
            this.source = input;  
            this.contentHandler.setDocumentLocator(this);
            serializer.reset();
            serializer.setSAXHandlers(this.contentHandler, null);
            serializer.toSAX(source.getDocument());
    
            this.contentHandler.endDocument();
        }
        catch (SAXParseException e) {
            LOG.error("SaxParseException: {}", e);
            try {
                this.errHandler.error(e);
            } catch (Exception e2) {
                LOG.error("Exception handling exception: {}", e2);
            }
        } catch (Exception e) {
            LOG.error("Exception: {}", e);
            try {
		/* FIXME:  Do we need to forward the exception to the errHandler, or has this
                 * been done for us? - PLM
		 */
                this.errHandler.error(new SAXParseException("Unable to parse document", null, e));
            } catch (Exception e2) {
                LOG.error("Exception handling exception: {}", e2);
            }

        } finally {
            this.source = null;
        }
    }
    
    @Override
    public void parse(final String systemId) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void setContentHandler(final ContentHandler handler) {
        this.contentHandler = handler;
    }
    
    @Override
    public void setDTDHandler(final DTDHandler handler) {
    }
    
    @Override
    public void setEntityResolver(final EntityResolver resolver) {
    }
    
    @Override
    public void setErrorHandler(final ErrorHandler handler) {
        this.errHandler = handler;
    }
    
    @Override
    public void setFeature(final String name, final boolean value) {
    }
    
    @Override
    public void setProperty(final String name, final Object value) {
    }
   
    @Override
    public int getColumnNumber() {
	// FIXME:  Can we do better than -1?
        return -1;
    }
    
    @Override
    public int getLineNumber() {
	// FIXME:  Can we do better than -1?
        return -1;
    }
    
    @Override
    public String getSystemId() {
        return this.source == null ? null : this.source.getSystemId(); 
    }

    @Override
    public String getPublicId() {
        return this.source == null ? null : this.source.getPublicId(); 
    }
}

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

import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;

/**
 * {@link javax.xml.transform.sax.SAXSource} Supplying an XML document from the eXist database.
 *
 * @author <a href="mailto:Paul.L.Merchant.Jr@dartmouth.edu">Paul Merchant, Jr.</a>
 */


public class EXistDbSource extends SAXSource {
    private final static Logger LOG = LogManager.getLogger(EXistDbSource.class);

    private InputSource source;
    
    public EXistDbSource(final DBBroker broker, final DocumentImpl doc) {
        this.source = new EXistDbInputSource(broker, doc);
    }

    @Override
    public InputSource getInputSource() { 
        return this.source;
    }
    
    @Override
    public String getSystemId() {
        return (this.source == null) ? null : this.source.getSystemId();
    }
    
    @Override
    public XMLReader getXMLReader() {
	/* FIXME:  Should the reader be configured to read our InputSource before returning?
         * Apparently Saxon configures it later with our InputSource, leaving this an open question.
	 */
        return new EXistDbXMLReader();
    }
    
    @Override
    public void setInputSource(final InputSource inputSource) {
        if (!(inputSource instanceof EXistDbInputSource)) {
            throw new UnsupportedOperationException("EXistDbSource only accepts EXistDbInputSource");
        }
        
        this.source = inputSource;
    }
    
    @Override
    public void setSystemId(final String systemId) {
	if (this.source == null) {
	    // This should not be possible
	    throw new IllegalStateException("EXistDbSource cannot initialize InputSource from a systemId");
	}
        this.source.setSystemId(systemId);
    }
    
    @Override
    public void setXMLReader(final XMLReader reader) {
	throw new UnsupportedOperationException("Setting external reader is not supported");
    }
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.source;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;

/**
 * Source implementation that reads from a binary resource
 * stored in the database.
 * 
 * @author wolf
 */
public class DBSource extends AbstractSource {
    
    private BinaryDocument doc;
    private XmldbURI key;
    private long lastModified;
    private String encoding = "UTF-8";
    private boolean checkEncoding;
    private DBBroker broker;
    
    public DBSource(DBBroker broker, BinaryDocument doc, boolean checkXQEncoding) {
        this.broker = broker;
        this.doc = doc;
        this.key = doc.getURI();
        this.lastModified = doc.getMetadata().getLastModified();
        this.checkEncoding = checkXQEncoding;
    }
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#getKey()
     */
    public Object getKey() {
        return key;
    }

    public XmldbURI getDocumentPath() {
    	return key;
    }

    public long getLastModified() {
        return lastModified;
    }
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#isValid()
     */
    public int isValid(DBBroker broker) {
        DocumentImpl doc = null;
        try {
            doc = broker.getXMLResource(key, Lock.READ_LOCK);
            if (doc == null)
                return INVALID;
            if (doc.getMetadata().getLastModified() > lastModified)
                return INVALID;
            return VALID;
        } catch (PermissionDeniedException e) {
            return INVALID;
        } finally {
            if (doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#isValid(org.exist.source.Source)
     */
    public int isValid(Source other) {
        if(!(other instanceof DBSource))
            return INVALID;
        DBSource source = (DBSource) other;
        if(source.getLastModified() > lastModified)
            return INVALID;
        return VALID;
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#getReader()
     */
    public Reader getReader() throws IOException {
        InputStream is = broker.getBinaryResource(doc);
        BufferedInputStream bis = new BufferedInputStream(is);
        bis.mark(64);
        checkEncoding(bis);
        bis.reset();
        return new InputStreamReader(bis, encoding);
    }

    public InputStream getInputStream() throws IOException {
        return broker.getBinaryResource(doc);
    }

    /* (non-Javadoc)
     * @see org.exist.source.Source#getContent()
     */
    public String getContent() throws IOException {
        InputStream raw = broker.getBinaryResource(doc);
        byte [] data = new byte[(int)broker.getBinaryResourceSize(doc)];
        raw.read(data);
        raw.close();
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        checkEncoding(is);
        return new String(data, encoding);
    }

    private void checkEncoding(InputStream is) throws IOException {
        if (checkEncoding) {
            String checkedEnc = guessXQueryEncoding(is);
            if (checkedEnc != null)
                encoding = checkedEnc;
        }
    }
    
    public String toString() {
    	return doc.getDocumentURI();
    }
}

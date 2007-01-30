/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.validation.internal;

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.xmldb.XmldbURI;


/**
 *  Wrapper between ResourceThread that writes data into outputstream and
 * the  needed InputStream that is needed for the Validator. The glue is the
 * circulair buffer BlockingOutputStream.
 *
 * @author dizzzz
 * @see java.io.InputStream
 * @see org.exist.validation.Validator
 * @see org.exist.validation.internal.BlockingOutputStream
 */
public class ResourceInputStream extends InputStream {
    
    private final static Logger logger = Logger.getLogger(ResourceInputStream.class);
    private BlockingOutputStream bis = null;
    private ResourceThread rt = null;
    
    /**
     * Creates a new instance of ResourceInputStream.
     *
     * @param brokerPool          BrokerPool
     * @param docUri    XML resource that must be streamed.
     */
    public ResourceInputStream(BrokerPool brokerPool, XmldbURI docUri) {
        
        logger.debug("Initializing ResourceInputStream");
        
        bis = new BlockingOutputStream();
        
        rt = new ResourceThread(brokerPool, docUri, bis);
        
        rt.start();
        
        logger.debug("Initializing ResourceInputStream done");
    }
    
    public int read(byte[] b, int off, int len) throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        return bis.read(b, off, len);
    }
    
    public int read(byte[] b) throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        return bis.read(b, 0, b.length);
    }
    
//    public void mark(int readlimit) {
//
//        bis.mark(readlimit);
//    }
    
    public long skip(long n) throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        return super.skip(n);
    }
    
    public void reset() throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        super.reset();
    }
    
    public int read() throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        return bis.read();
    }
    
//    public boolean markSupported() {
//
//        boolean retValue;
//
//        retValue = bis.markSupported();
//        return retValue;
//    }
    
    public void close() throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        bis.close();
    }
    
    //DWES
    public void flush() throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        bis.flush();
    }
    
    
    public int available() throws java.io.IOException {
        if(rt.isExceptionThrown()) {
            throw new IOException(rt.getThrownException().getMessage());
        }
        return bis.available();
    }
    
}

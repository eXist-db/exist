/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id: XmlrpcOutputStream.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.xmlrpc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;

import org.apache.log4j.Logger;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.io.BlockingInputStream;
import org.exist.storage.io.BlockingOutputStream;

/**
 * Write document to remote database (using xmlrpc) using output stream.
 *
 * @author Dannes Wessels
 */
public class XmlrpcOutputStream  extends OutputStream {
    
    
    private final static Logger logger = Logger.getLogger(XmlrpcOutputStream.class);
    private BlockingInputStream bis;
    private BlockingOutputStream bos;
    private XmlrpcUploadThread rt; 
    
    /**
     *  Constructor of XmlrpcOutputStream. 
     * 
     * @param xmldbURL Location of document in database.
     * @throws MalformedURLException Thrown for illegalillegal URLs.
     */
    public XmlrpcOutputStream(XmldbURL xmldbURL) {
        
        logger.debug("Initializing XmlrpcOutputStream");
        
        bis = new BlockingInputStream();
        bos = bis.getOutputStream();
        
        rt = new XmlrpcUploadThread(xmldbURL, bis);
        rt.start();
        
        logger.debug("Initializing XmlrpcOutputStream done");
    }

    
    public void write(int b) throws IOException {
        bos.write(b);
    }

    public void write(byte[] b) throws IOException {
        bos.write(b,0,b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        bos.write(b,off,len);
    }

    public void close() throws IOException {
        bos.close();
    }

    public void flush() throws IOException {
        bos.flush();
    }
}

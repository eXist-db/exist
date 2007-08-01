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
 * $Id: EmbeddedUploadThread.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;


import java.io.IOException;

import org.apache.log4j.Logger;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.io.BlockingInputStream;

/**
 *  Wrap XmlrpcUpload class into a thread for XmlrpcOutputStream.
 *
 * @author Dannes Wessels
 */
public class EmbeddedUploadThread extends Thread {
    
    private final static Logger logger = Logger.getLogger(EmbeddedUploadThread.class);
    private XmldbURL xmldbURL;
    private BlockingInputStream bis;
    
    public EmbeddedUploadThread(XmldbURL url, BlockingInputStream bis) {
        xmldbURL=url;
        this.bis=bis;
    }
    
    /**
     * Start Thread.
     */
    public void run() {
        logger.debug("Thread started." );
        IOException exception=null;
        try {
            EmbeddedUpload uploader = new EmbeddedUpload();
            uploader.stream(xmldbURL, bis);
            
        } catch (IOException ex) {
            logger.error(ex);
            exception = ex;
            
        } finally {
            bis.close(exception);
            logger.debug("Thread stopped." );
        }
    }
}

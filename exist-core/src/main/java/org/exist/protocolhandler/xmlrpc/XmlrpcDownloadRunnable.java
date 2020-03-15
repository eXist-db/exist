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

package org.exist.protocolhandler.xmlrpc;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.io.BlockingOutputStream;

/**
 * Wrap XmlrpcDownload class into a runnable for XmlrpcInputStream.
 *
 * @author Dannes Wessels
 */
public class XmlrpcDownloadRunnable implements Runnable {

    private static final Logger logger = LogManager.getLogger(XmlrpcDownloadRunnable.class);
    private final XmldbURL url;
    private final BlockingOutputStream bos;

    /**
     * Constructor of XmlrpcDownloadThread.
     *
     * @param url Document location in database.
     * @param bos Stream to which the document is written.
     */
    public XmlrpcDownloadRunnable(final XmldbURL url, final BlockingOutputStream bos) {
        this.url = url;
        this.bos = bos;
    }

    /**
     * Write resource to the output stream.
     */
    @Override
    public void run() {
        IOException exception = null;
        try {
            final XmlrpcDownload xuc = new XmlrpcDownload();
            xuc.stream(url, bos);

        } catch (final IOException ex) {
            logger.error(ex);
            exception = ex;

        } finally {
            try { // NEEDED!
                bos.close(exception);
            } catch (final IOException ex) {
                logger.warn(ex);
            }
        }
    }
}

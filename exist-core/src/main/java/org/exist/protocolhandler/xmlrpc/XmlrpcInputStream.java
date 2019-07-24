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
 * $Id: XmlrpcInputStream.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.xmlrpc;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.io.BlockingInputStream;

/**
 * Read document from remote database (using xmlrpc) as a input stream.
 *
 * @author Dannes Wessels
 */
public class XmlrpcInputStream extends InputStream {
    private static final AtomicInteger downloadThreadId = new AtomicInteger();
    private final BlockingInputStream bis;

    /**
     * Constructor of XmlrpcInputStream.
     *
     * @param threadGroup the group for the threads created by this stream.
     * @param url         Location of document in database.
     */
    public XmlrpcInputStream(final ThreadGroup threadGroup, final XmldbURL url) {
        this.bis = new BlockingInputStream();

        final Runnable runnable = new XmlrpcDownloadRunnable(url, bis.getOutputStream());
        final Thread thread = new Thread(threadGroup, runnable, threadGroup.getName() + ".xmlrpc.download-" + downloadThreadId.getAndIncrement());
        thread.start();
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return bis.read(b, off, len);
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return bis.read(b, 0, b.length);
    }

    @Override
    public long skip(final long n) throws IOException {
        return bis.skip(n);
    }

    @Override
    public void reset() throws IOException {
        bis.reset();
    }

    @Override
    public int read() throws IOException {
        return bis.read();
    }

    @Override
    public void close() throws IOException {
        bis.close();
    }

    @Override
    public int available() throws IOException {
        return bis.available();
    }
}

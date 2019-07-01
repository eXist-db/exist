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
import java.util.concurrent.atomic.AtomicInteger;

import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.io.BlockingInputStream;
import org.exist.storage.io.BlockingOutputStream;

/**
 * Write document to remote database (using xmlrpc) using output stream.
 *
 * @author Dannes Wessels
 */
public class XmlrpcOutputStream extends OutputStream {
    private static final AtomicInteger uploadThreadId = new AtomicInteger();
    private final BlockingOutputStream bos;

    /**
     * Constructor of XmlrpcOutputStream.
     *
     * @param threadGroup the group for the threads created by this stream.
     * @param url         Location of document in database.
     */
    public XmlrpcOutputStream(final ThreadGroup threadGroup, final XmldbURL url) {
        final BlockingInputStream bis = new BlockingInputStream();
        this.bos = bis.getOutputStream();

        final Runnable runnable = new XmlrpcUploadRunnable(url, bis);
        final Thread thread = new Thread(threadGroup, runnable, threadGroup.getName() + ".xmlrpc.upload-" + uploadThreadId.getAndIncrement());
        thread.start();
    }

    @Override
    public void write(final int b) throws IOException {
        bos.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        bos.write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        bos.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        bos.close();
    }

    @Override
    public void flush() throws IOException {
        bos.flush();
    }
}

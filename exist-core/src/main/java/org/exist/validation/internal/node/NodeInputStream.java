/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  $Id$
 */

package org.exist.validation.internal.node;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

import org.exist.Database;
import org.exist.storage.io.BlockingInputStream;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.value.NodeValue;

import static org.exist.util.ThreadUtils.newInstanceThread;

/**
 * Node to Inputstream serializer.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class NodeInputStream extends InputStream {
    private final BlockingInputStream bis;

    private static final AtomicLong nodeSerializerThreadId = new AtomicLong();

    /**
     * Creates a new instance of NodeInputStream.
     *
     * @param database      The database.
     * @param serializer    The serializer.
     * @param node          The node that is serialized.
     */
    public NodeInputStream(final Database database, final Serializer serializer, final NodeValue node) {
        this.bis = new BlockingInputStream();
        final Thread thread = newInstanceThread(database, "node-input-stream-serializer-" + nodeSerializerThreadId.getAndIncrement(), new NodeSerializerRunnable(serializer, node, bis.getOutputStream()));
        thread.start();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return bis.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return bis.read(b, 0, b.length);
    }

    @Override
    public long skip(long n) throws IOException {
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

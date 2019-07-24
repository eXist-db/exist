/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util.io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class VirtualTempPathTest {
    private TemporaryFileManager temporaryFileManager;
    private VirtualTempPath virtualTempPath;

    @Before
    public void setUp() {
        temporaryFileManager = TemporaryFileManager.getInstance();
        virtualTempPath = new VirtualTempPath(2048, temporaryFileManager);
    }

    @After
    public void tearDown() {
        virtualTempPath.close();
    }

    @Test
    public void newOutputStreamMemoryOnly() throws IOException {
        OutputStream out = virtualTempPath.newOutputStream();

        assertEquals(OverflowToDiskStream.class, out.getClass());
        byte[] buf = writeTestData(out, 1024);

        assertArrayEquals(buf, virtualTempPath.getBytes());
    }

    @Test
    public void newOutputStreamNoMemoryBuffer() throws IOException {
        virtualTempPath = new VirtualTempPath(0, temporaryFileManager);
        OutputStream out = virtualTempPath.newOutputStream();

        assertNotEquals(OverflowToDiskStream.class, out.getClass());
        byte[] buf = writeTestData(out, 1024);

        assertArrayEquals(buf, virtualTempPath.getBytes());
    }

    @Test
    public void newInputStreamUseEmptyInputStreamIfNotAlreadyWritten() throws IOException {
        InputStream in = virtualTempPath.newInputStream();

        assertEquals(ByteArrayInputStream.class, in.getClass());
    }

    @Test
    public void newInputStreamAfterDataWrittenInMemory() throws IOException {
        byte[] buf = writeTestData(virtualTempPath.newOutputStream(), 1024);

        InputStream in = virtualTempPath.newInputStream();

        assertEquals(MemoryContentsInputStream.class, in.getClass());

        assertArrayEquals(buf, readAllBytes(in));
        assertArrayEquals(buf, virtualTempPath.getBytes());
    }

    @Test
    public void newInputStreamAfterDataWrittenToDisk() throws IOException {
        byte[] buf = writeTestData(virtualTempPath.newOutputStream(), 2048);

        InputStream in = virtualTempPath.newInputStream();

        assertNotEquals(MemoryContentsInputStream.class, in.getClass());

        assertArrayEquals(buf, readAllBytes(in));
        assertArrayEquals(buf, virtualTempPath.getBytes());
    }

    @Test
    public void sizeNotYetWritten() {
        assertEquals(0, virtualTempPath.size());
    }

    @Test
    public void sizeNWrittenInMemory() throws IOException {
        writeTestData(virtualTempPath.newOutputStream(), 123);

        assertEquals(123L, virtualTempPath.size());
    }

    @Test
    public void sizeNWrittenToDisk() throws IOException {
        writeTestData(virtualTempPath.newOutputStream(), 2123);

        assertEquals(2123L, virtualTempPath.size());
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int read = 0;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private byte[] writeTestData(OutputStream out, int size) throws IOException {
        byte[] buf = new byte[size];
        buf[size - 1] = 'x';
        out.write(buf);
        out.close();
        return buf;
    }
}

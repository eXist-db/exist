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

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class MemoryContentsOutputStreamTest {
    private MemoryContents memoryContents;
    private MemoryContentsOutputStream outputStream;

    @Before
    public void setUp() {
        memoryContents = createMock(MemoryContents.class);
        outputStream = new MemoryContentsOutputStream(memoryContents);
    }

    @Test
    public void writeByte() throws IOException {
        expect(memoryContents.writeAtEnd(aryEq(new byte[]{'a'}), eq(0), eq(1))).andReturn(1);

        replay(memoryContents);

        outputStream.write('a');

        verify(memoryContents);
    }

    @Test
    public void writeByteArray() throws IOException {
        byte[] buf = new byte[]{'1', '2', '3', '4', '5', '6', '7', '8', '9'};

        expect(memoryContents.writeAtEnd(buf, 2, 3)).andReturn(1);

        replay(memoryContents);

        outputStream.write(buf, 2, 3);

        verify(memoryContents);
    }
}

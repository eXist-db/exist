/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2019 The eXist Project
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

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public final class ByteArrayContent implements ContentFile {
    private static final byte[] EMPTY_BUFFER = new byte[0];

    private byte[] data;

    public static ByteArrayContent of(byte[] data) {
        return new ByteArrayContent(data);
    }

    public static ByteArrayContent of(String data) {
        return new ByteArrayContent(data.getBytes(StandardCharsets.UTF_8));
    }

    private ByteArrayContent(byte[] data) {
        this.data = data;
    }

    @Override
    public void close() {
        data = null;
    }

    @Override
    public byte[] getBytes() {
        return data == null ? EMPTY_BUFFER : data;
    }

    @Override
    public long size() {
        return data == null ? 0 : data.length;
    }
}

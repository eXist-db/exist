/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.journal;

import org.exist.util.ByteConversion;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Log Sequence Number: identifies a log record within the journal file.
 * A LSN is represented by a Java long and consists of the file number
 * of the journal file and an offset into the file.
 *
 * An LSN is 10 bytes, the first 8 bytes are the offset, the last 2 bytes
 * are the fileNumber. The LSN is in <i>big-endian</i> byte-order: the
 * most significant byte is in the zeroth element.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Lsn implements Comparable<Lsn> {

    /**
     * Length of the LSN in bytes.
     */
    public static final int RAW_LENGTH = 10;
    private static final int FILE_NUMBER_OFFSET = 0;
    private static final int FILE_OFFSET_OFFSET = 2;

    /**
     * This mask is used to obtain the value of an int as if it were unsigned.
     */
    private static final long LONG_MASK = 0xffffffffL;

    /**
     * Singleton which represents an Invalid LSN
     */
	public static final Lsn LSN_INVALID = new Lsn((short)-1, -1l);

    private final byte[] lsn;

    public Lsn(final short fileNumber, final long offset) {
        this.lsn = new byte[RAW_LENGTH];
        ByteConversion.shortToByteH(fileNumber, lsn, FILE_NUMBER_OFFSET);
        ByteConversion.longToByte(offset, lsn, FILE_OFFSET_OFFSET);
    }

    private Lsn(final byte data[], final int offset) {
        this.lsn = new byte[RAW_LENGTH];
        System.arraycopy(data, offset, lsn, 0, RAW_LENGTH);
    }

    private Lsn(final byte lsn[]) {
        this.lsn = lsn;
    }

    public static Lsn read(final byte[] lsn, final int offset) {
        return new Lsn(lsn, offset);
    }

    public static Lsn read(final ByteBuffer buffer) {
        final byte[] lsn = new byte[RAW_LENGTH];
        buffer.get(lsn);
        return new Lsn(lsn);
    }

    public void write(final byte[] buffer, final int offset) {
        System.arraycopy(lsn, 0, buffer, offset, RAW_LENGTH);
    }

    public void write(final ByteBuffer buffer) {
        buffer.put(lsn);
    }

    /**
     * Returns the file number encoded in the passed LSN.
     *
     * @return file number
     */
    public long getFileNumber() {
        return ByteConversion.byteToShortH(lsn, FILE_NUMBER_OFFSET);
    }

    /**
     * Returns the file offset encoded in the passed LSN.
     *
     * @return file offset
     */
    public long getOffset() {
        return ByteConversion.byteToLong(lsn, FILE_OFFSET_OFFSET);
    }

    @Override
    public int compareTo(final Lsn other) {
        final boolean thisInvalid = this == LSN_INVALID || LSN_INVALID.equals(this);
        final boolean otherInvalid = other == LSN_INVALID || LSN_INVALID.equals(other);

        if (thisInvalid && otherInvalid) {
            return 0;
        } else if(thisInvalid && !otherInvalid) {
            return -1;
        } else if (otherInvalid && !thisInvalid) {
            return 1;
        }

        for (int i = 0; i < RAW_LENGTH; i++) {
            int a = lsn[i];
            int b = other.lsn[i];
            if (a != b)
                return ((a & LONG_MASK) < (b & LONG_MASK)) ? -1 : 1;
        }
        return 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Lsn other = (Lsn) o;
        return Arrays.equals(lsn, other.lsn);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(lsn);
    }

    @Override
    public String toString() {
        return getFileNumber() + ", " + getOffset();
    }
}

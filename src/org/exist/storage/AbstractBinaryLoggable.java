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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public abstract class AbstractBinaryLoggable extends AbstractLoggable {
    private static final Logger LOG = LogManager.getLogger(AbstractBinaryLoggable.class);

    protected final static byte NO_DIGEST_TYPE = 0x0;

    public AbstractBinaryLoggable(final byte type, final long transactionId) {
        super(type, transactionId);
    }

    /**
     * Get's the absolute path as a byte array.
     *
     * @param path the path to encode.
     *
     * @return the absolute path, UTF-8 encoded as bytes
     */
    @Nullable protected static byte[] getPathData(@Nullable final Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().toString().getBytes(UTF_8);
    }

    /**
     * Get's the path from a byte array encoded by {@link #getPathData(Path)}.
     *
     * @param pathData the path data to decode.
     *
     * @return the path
     */
    @Nullable protected static Path getPath(@Nullable final byte[] pathData) {
        if (pathData == null) {
            return null;
        }
        return Paths.get(new String(pathData, UTF_8));
    }

    /**
     * Converts the first two bytes of an integer into
     * an unsigned short and stores the result into a short.
     *
     * @param i the integer
     *
     * @return the unsigned short stored in a short.
     */
    protected static short asUnsignedShort(final int i) {
        return (short)(i & 0xFFFF);
    }

    /**
     * Converts an unsigned short stored in a short back
     * into an integer. Inverse of {@link #asUnsignedShort(int)}.
     *
     * @param s the unsigned short as a short.
     *
     * @return the integer.
     */
    protected static int asSignedInt(final short s) {
        return s & 0xFFFF;
    }

    /**
     * Check that the length of a path does not need more storage than we have available (i.e. 2 bytes).
     *
     * @param loggableName The name of the loggable (for formatting error messages).
     * @param pathName The name of the path (for formatting error messages).
     * @param path The path to check the length of.
     */
    protected static void checkPathLen(final String loggableName, final String pathName, @Nullable final byte path[]) {
        if (path == null) {
            return;
        }

        final int len = path.length;
        if (len <= 0) {
            LOG.error(loggableName + ": " + pathName + " path has a zero length");
        } else if(len > 0xFFFF) {
            LOG.error(loggableName + ": " + pathName + " path needs more than 65,535 bytes. Path will be truncated: " + new String(path, UTF_8));
        }
    }

    /**
     * Writes a message digest to a buffer.
     *
     * @param out the buffer to write the message digest to.
     * @param messageDigest the message digest to write to the buffer.
     */
    protected static void writeMessageDigest(final ByteBuffer out, @Nullable final MessageDigest messageDigest) {
        if (messageDigest == null) {
            out.put(NO_DIGEST_TYPE);
        } else {
            out.put(messageDigest.getDigestType().getId());
            out.put(messageDigest.getValue(), 0, messageDigest.getDigestType().getDigestLengthBytes());
        }
    }

    /**
     * Reads a message digest from a buffer.
     *
     * @param in the buffer to read the message digest from.
     *
     * @return the message digest read from the buffer.
     */
    protected static @Nullable MessageDigest readMessageDigest(final ByteBuffer in) {
        final byte digestTypeId = in.get();
        if (digestTypeId == NO_DIGEST_TYPE) {
            return null;
        } else {
            final DigestType digestType = DigestType.forId(digestTypeId);
            final byte[] digestValue = new byte[digestType.getDigestLengthBytes()];
            in.get(digestValue);
            return new MessageDigest(digestType, digestValue);
        }
    }
}

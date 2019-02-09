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

package org.exist.util.crypto.digest;

import org.bouncycastle.crypto.ExtendedDigest;
import org.bouncycastle.crypto.digests.MD4Digest;

import java.util.Arrays;

import static org.exist.util.crypto.digest.DigestType.MD_4;

/**
 * Implementation of MD4 streamable digest.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class MD4StreamableDigest implements StreamableDigest {
    private final ExtendedDigest ed = new MD4Digest();

    @Override
    public void update(final byte b) {
        ed.update(b);
    }

    @Override
    public void update(final byte[] buf, final int offset, final int len) {
        ed.update(buf, offset, len);
    }

    @Override
    public DigestType getDigestType() {
        return MD_4;
    }

    @Override
    public byte[] getMessageDigest() {
        final byte[] digestBytes = new byte[MD_4.getDigestLengthBytes()];
        ed.doFinal(digestBytes, 0);
        return digestBytes;
    }

    @Override
    public MessageDigest copyMessageDigest() {
        return new MessageDigest(MD_4,
                Arrays.copyOf(getMessageDigest(), MD_4.getDigestLengthBytes())
        );
    }

    @Override
    public void reset() {
        ed.reset();
    }
}

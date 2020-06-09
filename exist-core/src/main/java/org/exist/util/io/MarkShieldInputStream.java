/*
 * Copyright (c) 2012, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Adam Retter Consulting nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.util.io;

import org.apache.commons.io.input.ProxyInputStream;

import java.io.IOException;
import java.io.InputStream;

// TODO(AR) remove this code when https://github.com/apache/commons-io/pull/119 is merged and released

/**
 * This is an alternative to {@link java.io.ByteArrayInputStream}
 * which removes the synchronization overhead for non-concurrent
 * access; as such this class is not thread-safe.
 *
 * Proxy stream that prevents the underlying input stream from being marked/reset.
 * <p>
 * This class is typically used in cases where an input stream that supports
 * marking needs to be passed to a component that wants to explicitly mark
 * the stream, but it it is not desirable to allow marking of the stream.
 * </p>
 *
 * @since 2.8
 */
public class MarkShieldInputStream extends ProxyInputStream {

    /**
     * Creates a proxy that shields the given input stream from being
     * marked or rest.
     *
     * @param in underlying input stream
     */
    public MarkShieldInputStream(final InputStream in) {
        super(in);
    }

    @SuppressWarnings("sync-override")
    @Override
    public void mark(final int readlimit) {
        // no-op
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @SuppressWarnings("sync-override")
    @Override
    public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }
}

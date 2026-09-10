/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http.urlrewrite;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.exist.http.servlets.HeaderPassthroughResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Buffers a single controller.xql pipeline step's response -- status, headers, and body -- so
 * {@link XQueryURLRewrite} can decide, only after the step has actually run, whether this step's
 * output becomes the final response or instead feeds a subsequent {@code <exist:view>} step as
 * that step's request body. A step whose output is superseded this way has its wrapper simply
 * discarded, unflushed; only {@link #flush()} on the wrapper that is the pipeline's actual final
 * step commits anything to the real response.
 *
 * @see HeaderPassthroughResponse
 */
class CachingResponseWrapper extends HttpServletResponseWrapper implements HeaderPassthroughResponse {

    /**
     * Tracks which of {@link #getWriter()} / {@link #getOutputStream()} this response has
     * committed to, per the servlet spec mutual-exclusion rule. Unlike using {@code sos != null}
     * as the sentinel (the previous approach), this distinguishes "a stream backing the writer
     * already exists" from "getOutputStream() was called directly" -- so a repeat call to
     * whichever method was called first is idempotent, matching real servlet container
     * responses. This idempotency is required by Jetty 12's {@code Dispatcher.forward()}, which
     * -- after the forwarded servlet returns -- itself calls {@code getOutputStream()} and falls
     * back to {@code getWriter()} on {@link IllegalStateException} in order to close whichever
     * stream is live, regardless of which one the forwarded servlet used.
     */
    private enum OutputMode { NONE, WRITER, STREAM }

    private OutputMode outputMode = OutputMode.NONE;
    private CachingServletOutputStream sos = null;
    private PrintWriter writer = null;
    private int status = HttpServletResponse.SC_OK;
    private String contentType = null;
    private final boolean cache;

    CachingResponseWrapper(final HttpServletResponse servletResponse, final boolean cache) {
        super(servletResponse);
        this.cache = cache;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (!cache) {
            return super.getWriter();
        }
        if (outputMode == OutputMode.STREAM) {
            // Per the ServletResponse#getWriter() contract, this must be an IllegalStateException,
            // not an IOException -- Jetty 12's Dispatcher.forward() relies on catching exactly
            // this type to fall back from getOutputStream() to getWriter().
            throw new IllegalStateException("getWriter cannot be called after getOutputStream");
        }
        if (outputMode == OutputMode.NONE) {
            // Only commit outputMode/sos/writer once construction has fully succeeded -- if
            // getCharacterEncoding() names a charset the JVM doesn't support, OutputStreamWriter
            // throws, and a half-committed state here (mode flipped but writer still null) would
            // make every subsequent getWriter() call silently return null instead of retrying.
            final CachingServletOutputStream newSos = new CachingServletOutputStream();
            final PrintWriter newWriter = new PrintWriter(new OutputStreamWriter(newSos, getCharacterEncoding()));
            sos = newSos;
            writer = newWriter;
            outputMode = OutputMode.WRITER;
        }
        return writer;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (!cache) {
            return super.getOutputStream();
        }
        if (outputMode == OutputMode.WRITER) {
            // See getWriter(): must be IllegalStateException, not IOException.
            throw new IllegalStateException("getOutputStream cannot be called after getWriter");
        }
        if (outputMode == OutputMode.NONE) {
            sos = new CachingServletOutputStream();
            outputMode = OutputMode.STREAM;
        }
        return sos;
    }

    public byte[] getData() {
        return sos != null ? sos.getData() : null;
    }

    @Override
    public void setContentType(final String type) {
        if (contentType != null) {
            return;
        }
        this.contentType = type;
        if (!cache) {
            super.setContentType(type);
        }
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType : super.getContentType();
    }

    /**
     * A header write this step made while buffering, recorded so it can be replayed -- in the
     * exact original order, through the exact original typed method -- onto the real response
     * from {@link #flush()}, but only if this wrapper is the one that actually gets flushed.
     * If a later {@code <exist:view>} step replaces this step's output, this wrapper (and
     * everything buffered in it) is simply discarded without ever being replayed: correct,
     * because the header described a step whose output turned out not to be the final one.
     * <p>
     * Replaying the original call verbatim (rather than pre-computing a final name-to-value
     * map here) means the real response's own semantics decide what "wins" when the same
     * header is set multiple times (setHeader replaces, addHeader appends) -- exactly as they
     * would have if these calls had never been buffered at all.
     */
    private sealed interface BufferedHeader {
        record Set(String name, String value) implements BufferedHeader {
        }

        record Add(String name, String value) implements BufferedHeader {
        }

        record SetInt(String name, int value) implements BufferedHeader {
        }

        record AddInt(String name, int value) implements BufferedHeader {
        }

        record SetDate(String name, long value) implements BufferedHeader {
        }

        record AddDate(String name, long value) implements BufferedHeader {
        }
    }

    private final List<BufferedHeader> bufferedHeaders = new ArrayList<>();

    /**
     * Set a header on the real response immediately, bypassing buffering, tunnelling through
     * however many {@code CachingResponseWrapper} layers are stacked (a forward step may
     * itself trigger a nested dispatch) until reaching a response that isn't one of these
     * wrappers. See {@link HeaderPassthroughResponse}: this exists solely for
     * {@link URLRewrite#setHeaders}'s controller.xql {@code <exist:set-header>} directives,
     * which are pipeline configuration, not step output, and so must survive regardless of
     * which step's buffered output eventually wins.
     */
    @Override
    public void setPassthroughHeader(final String name, final String value) {
        if (getResponse() instanceof HeaderPassthroughResponse passthroughResponse) {
            passthroughResponse.setPassthroughHeader(name, value);
        } else {
            super.setHeader(name, value);
        }
    }

    @Override
    public void setHeader(final String name, final String value) {
        if ("Content-Type".equals(name)) {
            setContentType(value);
        } else if (cache) {
            bufferedHeaders.add(new BufferedHeader.Set(name, value));
        } else {
            super.setHeader(name, value);
        }
    }

    @Override
    public void addHeader(final String name, final String value) {
        if ("Content-Type".equals(name)) {
            // Route through setContentType() like setHeader() does -- otherwise a step that sets
            // Content-Type via addHeader() (e.g. Jetty's default/ResourceServlet serving a static
            // file) bypasses the contentType tracking entirely, which would leak it straight onto
            // the real response even though a later <exist:view> step's own Content-Type should
            // win instead.
            setContentType(value);
        } else if (cache) {
            bufferedHeaders.add(new BufferedHeader.Add(name, value));
        } else {
            super.addHeader(name, value);
        }
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        if (cache) {
            bufferedHeaders.add(new BufferedHeader.SetInt(name, value));
        } else {
            super.setIntHeader(name, value);
        }
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        if (cache) {
            bufferedHeaders.add(new BufferedHeader.AddInt(name, value));
        } else {
            super.addIntHeader(name, value);
        }
    }

    @Override
    public void setDateHeader(final String name, final long date) {
        if (cache) {
            bufferedHeaders.add(new BufferedHeader.SetDate(name, date));
        } else {
            super.setDateHeader(name, date);
        }
    }

    @Override
    public void addDateHeader(final String name, final long date) {
        if (cache) {
            bufferedHeaders.add(new BufferedHeader.AddDate(name, date));
        } else {
            super.addDateHeader(name, date);
        }
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(final int i) {
        this.status = i;
        super.setStatus(i);
    }


    @Override
    public void sendError(final int i, final String msg) throws IOException {
        this.status = i;
        super.sendError(i, msg);
    }

    @Override
    public void sendError(final int i) throws IOException {
        this.status = i;
        super.sendError(i);
    }

    @Override
    public void setContentLength(final int i) {
        if (!cache) {
            super.setContentLength(i);
        }
    }

    @Override
    public void setContentLengthLong(final long len) {
        // Without this override, HttpServletResponseWrapper's default delegates straight to the
        // real underlying response, leaking a step's Content-Length onto it even while cache=true.
        // A static resource served via Jetty's default/ResourceServlet (e.g. a plain file forward
        // step in a controller.xql pipeline) sets this rather than setContentLength(int), fixing
        // the real response's Content-Length before a later <exist:view> step's -- possibly
        // longer -- output is flushed to it. See https://github.com/eXist-db/exist/issues/6669
        if (!cache) {
            super.setContentLengthLong(len);
        }
    }

    @Override
    public void flushBuffer() throws IOException {
        if (!cache) {
            super.flushBuffer();
        }
    }

    public void flush() throws IOException {
        if (cache && contentType != null) {
            super.setContentType(contentType);
        }
        if (cache) {
            replayBufferedHeaders();
        }
        if (sos != null) {
            final byte[] data = sos.getData();
            // Set the real Content-Length explicitly rather than leaving it to the container to
            // infer (e.g. via chunked transfer encoding). Deliberately not part of the generic
            // replay above -- any buffered Content-Length (from this step's own setHeader/
            // setIntHeader/etc. call) is skipped there and superseded by this, because it's the
            // one header whose correctness is independently checkable against the bytes actually
            // being written, and trusting a self-reported value over that is exactly how #6669
            // happened.
            if (cache) {
                super.setContentLengthLong(data.length);
            }
            final ServletOutputStream out = super.getOutputStream();
            out.write(data);
            out.flush();
        }
    }

    private void replayBufferedHeaders() {
        for (final BufferedHeader header : bufferedHeaders) {
            final String name = switch (header) {
                case BufferedHeader.Set(final String n, final String ignored) -> n;
                case BufferedHeader.Add(final String n, final String ignored) -> n;
                case BufferedHeader.SetInt(final String n, final int ignored) -> n;
                case BufferedHeader.AddInt(final String n, final int ignored) -> n;
                case BufferedHeader.SetDate(final String n, final long ignored) -> n;
                case BufferedHeader.AddDate(final String n, final long ignored) -> n;
            };
            if ("Content-Length".equalsIgnoreCase(name)) {
                // See flush(): the real Content-Length is derived from the actual buffered byte
                // count, never trusted from a step's own self-reported value.
                continue;
            }
            switch (header) {
                case BufferedHeader.Set(final String n, final String value) -> super.setHeader(n, value);
                case BufferedHeader.Add(final String n, final String value) -> super.addHeader(n, value);
                case BufferedHeader.SetInt(final String n, final int value) -> super.setIntHeader(n, value);
                case BufferedHeader.AddInt(final String n, final int value) -> super.addIntHeader(n, value);
                case BufferedHeader.SetDate(final String n, final long value) -> super.setDateHeader(n, value);
                case BufferedHeader.AddDate(final String n, final long value) -> super.addDateHeader(n, value);
            }
        }
    }
}

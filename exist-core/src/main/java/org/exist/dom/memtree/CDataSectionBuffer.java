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
package org.exist.dom.memtree;

import org.xml.sax.ext.LexicalHandler;

/**
 * Reassembles a CDATA section from the SAX events that describe it.
 *
 * <p>A SAX parser reports the <em>content</em> of a CDATA section through
 * {@code characters()} like any other text, and marks the boundaries separately through
 * {@link LexicalHandler#startCDATA()} and {@link LexicalHandler#endCDATA()}. Rebuilding the
 * section therefore means buffering the characters that arrive between those two callbacks and
 * handing them to {@link MemTreeBuilder#cdataSection(CharSequence)} in one piece — a handler that
 * ignores the boundary callbacks silently turns every CDATA section into plain text, which is then
 * re-serialized escaped.</p>
 *
 * <p>Both SAX handlers that build an in-memory DOM need this and used to carry their own copy:
 * {@link SAXAdapter} (behind {@code fn:parse-xml}) and {@link DocumentBuilderReceiver} (behind
 * {@code request:get-data()}, among others). Keeping it in one place is what stops the two from
 * drifting apart again — they disagreed for years, which is
 * <a href="https://github.com/eXist-db/exist/issues/2081">issue #2081</a>.</p>
 */
final class CDataSectionBuffer {

    private final StringBuilder buffer = new StringBuilder();
    private boolean inCDataSection = false;

    /**
     * Whether the parser is currently inside a CDATA section, i.e. whether
     * {@link #append} should be preferred over writing characters straight to the builder.
     *
     * @return true between {@code startCDATA()} and {@code endCDATA()}
     */
    boolean isActive() {
        return inCDataSection;
    }

    /** Begins a section; call from {@code startCDATA()}. */
    void start() {
        this.inCDataSection = true;
    }

    /**
     * Buffers content reported inside the section.
     *
     * @param ch the character buffer
     * @param start offset of the content within {@code ch}
     * @param length length of the content
     */
    void append(final char[] ch, final int start, final int length) {
        buffer.append(ch, start, length);
    }

    /**
     * Buffers content reported inside the section.
     *
     * @param seq the content
     */
    void append(final CharSequence seq) {
        buffer.append(seq);
    }

    /**
     * Ends the section, appending everything buffered to {@code builder} as a CDATA node;
     * call from {@code endCDATA()}.
     *
     * @param builder the builder receiving the section
     */
    void flushTo(final MemTreeBuilder builder) {
        builder.cdataSection(buffer);
        buffer.setLength(0);
        this.inCDataSection = false;
    }
}

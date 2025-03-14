/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.dom.persistent;

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * XML Declaration of an XML document
 * available with SAX in Java 14+.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class XMLDeclarationImpl {

    @Nullable private final String version;
    @Nullable private final String encoding;
    @Nullable private final String standalone;

    public XMLDeclarationImpl(@Nullable final String version, @Nullable final String encoding, @Nullable final String standalone) {
        this.version = version;
        this.encoding = encoding;
        this.standalone = standalone;
    }

    /**
     * Get the version from the XML Declaration.
     *
     * @return the version (if present), or null.
     */
    @Nullable
    public String getVersion() {
        return version;
    }

    /**
     * Get the encoding from the XML Declaration.
     *
     * @return the encoding (if present), or null.
     */
    @Nullable
    public String getEncoding() {
        return encoding;
    }

    /**
     * Get the standalone from the XML Declaration.
     *
     * @return the standalone (if present), or null.
     */
    @Nullable
    public String getStandalone() {
        return standalone;
    }

    /**
     * Write the XML Declaration to the output stream.
     *
     * @param ostream the output stream.
     *
     * @throws IOException if an error occurs whilst writing to the output stream.
     */
    public void write(final VariableByteOutputStream ostream) throws IOException {
        ostream.writeUTF(version != null ? version : "");
        ostream.writeUTF(encoding != null ? encoding : "");
        ostream.writeUTF(standalone != null ? standalone : "");
    }

    /**
     * Read an XML Declaration from the input stream.
     *
     * @param istream the input stream.
     *
     * @throws IOException if an error occurs whilst reading from the input stream.
     */
    public static XMLDeclarationImpl read(final VariableByteInput istream) throws IOException {
        String version = istream.readUTF();
        if(version.isEmpty()) {
            version = null;
        }
        String encoding = istream.readUTF();
        if(encoding.isEmpty()) {
            encoding = null;
        }
        String standalone = istream.readUTF();
        if(standalone.isEmpty()) {
            standalone = null;
        }

        return new XMLDeclarationImpl(version, encoding, standalone);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("<?xml");

        if (version != null) {
            builder.append(" version=\"").append(version).append("\"");
        }

        if (encoding != null) {
            builder.append(" encoding=\"").append(encoding).append("\"");
        }

        if (standalone != null) {
            builder.append(" standalone=\"").append(standalone).append("\"");
        }

        builder.append("?>");

        return builder.toString();
    }
}

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
package org.exist.dom.persistent;

import net.jcip.annotations.ThreadSafe;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.IOException;

@ThreadSafe
public class DocumentTypeImpl extends StoredNode implements DocumentType {

    private final String publicId;
    private final String systemId;
    private final String name;

    public DocumentTypeImpl(final String name, final String publicId, final String systemId) {
        super(Node.DOCUMENT_TYPE_NODE);
        this.name = name;
        this.publicId = publicId;
        this.systemId = systemId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public NamedNodeMap getEntities() {
        return null;
    }

    @Override
    public NamedNodeMap getNotations() {
        return null;
    }

    @Override
    public String getInternalSubset() {
        return null;
    }

    protected void write(final VariableByteOutputStream ostream) throws IOException {
        ostream.writeUTF(name);
        ostream.writeUTF(systemId != null ? systemId : "");
        ostream.writeUTF(publicId != null ? publicId : "");
    }

    public static DocumentTypeImpl read(final VariableByteInput istream) throws IOException {
        final String name = istream.readUTF();
        String systemId = istream.readUTF();
        if(systemId.length() == 0) {
            systemId = null;
        }
        String publicId = istream.readUTF();
        if(publicId.length() == 0) {
            publicId = null;
        }

        return new DocumentTypeImpl(name, publicId, systemId);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("<!DOCTYPE ").append(name);

        if(publicId != null) {
            builder.append(" PUBLIC \"").append(publicId).append("\"");
        }

        if(systemId != null) {
            if(publicId == null) {
                builder.append(" SYSTEM");
            }
            builder.append(" \"").append(systemId).append("\"");
        }

        builder.append(" >");

        return builder.toString();
    }
}

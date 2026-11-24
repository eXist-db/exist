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
package org.exist.dom.persistent;

import net.jcip.annotations.ThreadSafe;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.xquery.Expression;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.annotation.Nullable;
import java.io.IOException;

@ThreadSafe
public class DocumentTypeImpl extends StoredNode implements DocumentType {

    private final String publicId;
    private final String systemId;
    private final String name;
    @Nullable private final String internalSubset;

    public DocumentTypeImpl(final String name, final String publicId, final String systemId) {
        this(null, name, publicId, systemId);
    }

    public DocumentTypeImpl(final Expression expression, final String name, final String publicId, final String systemId) {
        this(expression, name, publicId, systemId, null);
    }

    /**
     * @param expression the expression, or null
     * @param name the name of the document element
     * @param publicId the public id, or null
     * @param systemId the system id, or null
     * @param internalSubset the declarations of the internal subset that the database keeps (see
     *     {@link org.exist.dom.persistent.DocumentImpl#HAS_DOCTYPE_WITH_SUBSET}), or null
     */
    public DocumentTypeImpl(final Expression expression, final String name, final String publicId, final String systemId,
            @Nullable final String internalSubset) {
        super(expression, Node.DOCUMENT_TYPE_NODE);
        this.name = name;
        this.publicId = publicId;
        this.systemId = systemId;
        this.internalSubset = internalSubset;
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
    @Nullable
    public String getInternalSubset() {
        return internalSubset;
    }

    protected void write(final VariableByteOutputStream ostream) throws IOException {
        ostream.writeUTF(name);
        ostream.writeUTF(systemId != null ? systemId : "");
        ostream.writeUTF(publicId != null ? publicId : "");
        if (internalSubset != null) {
            ostream.writeUTF(internalSubset);
        }
    }

    /**
     * Reads a document type that was written without an internal subset, behind the flag
     * {@link DocumentImpl#HAS_DOCTYPE}.
     *
     * @param istream the input
     * @return the document type
     * @throws IOException if the document type cannot be read
     */
    public static DocumentTypeImpl read(final VariableByteInput istream) throws IOException {
        final String name = istream.readUTF();
        String systemId = istream.readUTF();
        if(systemId.isEmpty()) {
            systemId = null;
        }
        String publicId = istream.readUTF();
        if(publicId.isEmpty()) {
            publicId = null;
        }

        return new DocumentTypeImpl(null, name, publicId, systemId);
    }

    /**
     * Reads a document type that was written with an internal subset, behind the flag
     * {@link DocumentImpl#HAS_DOCTYPE_WITH_SUBSET}: the fields of {@link #read(VariableByteInput)} and the declarations.
     *
     * @param istream the input
     * @return the document type
     * @throws IOException if the document type cannot be read
     */
    public static DocumentTypeImpl readWithInternalSubset(final VariableByteInput istream) throws IOException {
        final DocumentTypeImpl withoutSubset = read(istream);
        return new DocumentTypeImpl(null, withoutSubset.name, withoutSubset.publicId, withoutSubset.systemId, istream.readUTF());
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

        if (internalSubset != null) {
            builder.append(" [").append(internalSubset).append("]");
        }

        builder.append(" >");

        return builder.toString();
    }
}

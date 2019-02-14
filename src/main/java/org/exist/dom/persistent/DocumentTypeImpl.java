/* eXist xml document repository and xpath implementation
 * Copyright (C) 2001-2014,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.dom.persistent;

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.IOException;

public class DocumentTypeImpl extends StoredNode implements DocumentType {

    protected String publicId = null;
    protected String systemId = null;
    protected String name = null;

    public DocumentTypeImpl() {
        super(Node.DOCUMENT_TYPE_NODE);
    }

    public DocumentTypeImpl(final String name, final String publicId, final String systemId) {
        super(Node.DOCUMENT_TYPE_NODE);
        this.name = name;
        this.publicId = publicId;
        this.systemId = systemId;
    }

    @Override
    public void clear() {
        super.clear();
        this.publicId = null;
        this.systemId = null;
        this.name = null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(final String publicId) {
        this.publicId = publicId;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(final String systemId) {
        this.systemId = systemId;
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

    protected void read(final VariableByteInput istream) throws IOException {
        name = istream.readUTF();
        systemId = istream.readUTF();
        if(systemId.length() == 0) {
            systemId = null;
        }
        publicId = istream.readUTF();
        if(publicId.length() == 0) {
            publicId = null;
        }
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

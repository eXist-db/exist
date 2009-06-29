/* eXist xml document repository and xpath implementation
 * Copyright (C) 2001,  Wolfgang M. Meier (wolfgang@exist-db.org)
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

package org.exist.dom;

import java.io.DataOutput;
import java.io.IOException;

import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class DocumentTypeImpl extends StoredNode implements DocumentType {

    protected String publicId = null;
    protected String systemId = null;
	protected String name = null;
	
    public DocumentTypeImpl() {
        super(Node.DOCUMENT_TYPE_NODE);
    }

    public DocumentTypeImpl(String name) {
        super(Node.DOCUMENT_TYPE_NODE);
        this.name = name;
    }

    public DocumentTypeImpl(String name, String publicId, String systemId) {
        super(Node.DOCUMENT_TYPE_NODE);
        this.name = name;
        this.publicId = publicId;
        this.systemId = systemId;
    }
    
    public void clear() {
        super.clear();   
        this.publicId = null;
        this.systemId = null;
        this.name = null;           
    }     

    public String getName() {
        return name;
    }
    
    public boolean hasChildNodes() {
        return false;
    } 

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public NamedNodeMap getEntities() {
        return null;
    }

    public NamedNodeMap getNotations() {
        return null;
    }

    public String getInternalSubset() {
        return null;
    }

    protected void write(DataOutput ostream) throws IOException {
        ostream.writeUTF(name);
        ostream.writeUTF(systemId != null ? systemId : "");
        ostream.writeUTF(publicId != null ? publicId : "");
    }

    protected void write(VariableByteOutputStream ostream) throws IOException {
        ostream.writeUTF(name);
        ostream.writeUTF(systemId != null ? systemId : "");
        ostream.writeUTF(publicId != null ? publicId : "");
    }

    protected void read(VariableByteInput istream) throws IOException {
        name = istream.readUTF();
        systemId = istream.readUTF();
        if (systemId.length() == 0)
            systemId = null;
        publicId = istream.readUTF();
        if (publicId.length() == 0)
            publicId = null;
    }

    protected void read(VariableByteArrayInput istream) throws IOException {
        name = istream.readUTF();
        systemId = istream.readUTF();
        if (systemId.length() == 0)
            systemId = null;
        publicId = istream.readUTF();
        if (publicId.length() == 0)
            publicId = null;
    }
}

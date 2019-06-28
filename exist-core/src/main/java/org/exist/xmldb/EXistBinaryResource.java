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

package org.exist.xmldb;

import org.exist.storage.blob.BlobId;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * Extensions for Binary Resources in eXist-db.
 */
public interface EXistBinaryResource extends BinaryResource {

    /**
     * Get the ID of the BLOB.
     *
     * @return the id of the BLOB.
     *
     * @throws XMLDBException if an error occurs retrieving the blobId.
     */
    BlobId getBlobId() throws XMLDBException;

    /**
     * Get the length of the binary content.
     *
     * @return the length of the binary content.
     *
     * @throws XMLDBException if an error occurs getting the content length.
     */
    long getContentLength() throws XMLDBException;

    /**
     * Get the digest of the BLOB content.
     *
     * @param digestType the message digest to use.
     *
     * @return the digest of the BLOB's content
     *
     * @throws XMLDBException if an error occurs getting the content digest.
     */
    MessageDigest getContentDigest(final DigestType digestType) throws XMLDBException;
}

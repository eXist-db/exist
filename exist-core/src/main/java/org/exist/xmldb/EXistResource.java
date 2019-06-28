/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import java.util.Date;
import java.util.Properties;

import org.exist.security.Permission;
import org.w3c.dom.DocumentType;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;

/**
 * Defines additional methods implemented by XML and binary
 * resources.
 *
 * @author wolf
 */
public interface EXistResource extends Resource, AutoCloseable {

    Date getCreationTime() throws XMLDBException;

    Date getLastModificationTime() throws XMLDBException;

    Permission getPermissions() throws XMLDBException;

    /**
     * The content length if known.
     *
     * @return The content length, or -1 if not known.
     *
     * @throws XMLDBException if an error occurs whilst getting the content's length.
     */
    long getContentLength() throws XMLDBException;

    void setLexicalHandler(LexicalHandler handler);

    void setMimeType(String mime);

    String getMimeType() throws XMLDBException;

    DocumentType getDocType() throws XMLDBException;

    void setDocType(DocumentType doctype) throws XMLDBException;

    void setLastModificationTime(Date lastModificationTime) throws XMLDBException;

    void freeResources() throws XMLDBException;

    void setProperties(Properties properties);

    @Nullable Properties getProperties();

    boolean isClosed();

    @Override
    void close() throws XMLDBException;
}

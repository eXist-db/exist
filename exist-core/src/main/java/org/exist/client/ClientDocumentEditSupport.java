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
package org.exist.client;

import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * Loads document content for the Java Admin Client editor off the EDT.
 * UI construction stays in {@link InteractiveClient#scheduleEditResource(org.exist.xmldb.XmldbURI)} (#4355).
 */
public final class ClientDocumentEditSupport {

    private ClientDocumentEditSupport() {
    }

    /**
     * Result of loading a resource for editing (XML:DB work only).
     */
    public record DocumentEditPayload(XmldbURI name, Resource resource) {
    }

    /**
     * Retrieves a resource to be shown in {@link DocumentView}.
     */
    @FunctionalInterface
    public interface DocumentRetriever {
        Resource retrieve() throws XMLDBException;
    }

    /**
     * Performs XML:DB retrieval only; must be called from a background thread.
     */
    public static DocumentEditPayload load(final XmldbURI name, final DocumentRetriever retriever) throws XMLDBException {
        return new DocumentEditPayload(name, retriever.retrieve());
    }
}

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
import org.junit.jupiter.api.Test;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientDocumentEditSupportTest {

    @Test
    void loadPropagatesXmlDbException() {
        final XmldbURI name = XmldbURI.create("/db/test.xml");
        assertThatThrownBy(() -> ClientDocumentEditSupport.load(name, () -> {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "retrieve failed");
        })).isInstanceOf(XMLDBException.class).hasMessageContaining("retrieve failed");
    }

    @Test
    void loadReturnsPayloadFromRetriever() throws XMLDBException {
        final XmldbURI name = XmldbURI.create("/db/doc.xml");
        final ClientDocumentEditSupport.DocumentEditPayload p = ClientDocumentEditSupport.load(name, () -> null);
        assertThat(p.name()).isSameAs(name);
        assertThat(p.resource()).isNull();
    }
}

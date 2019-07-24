/*
 * Copyright (C) 2018 Adam Retter
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
package org.exist.xquery.modules.expathrepo;

import org.exist.dom.persistent.BinaryDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.txn.Txn;
import org.expath.pkg.repo.XarSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * XAR Source for Binary Documents
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
class BinaryDocumentXarSource implements XarSource {
    private final BrokerPool pool;
    final Txn transaction;
    private final BinaryDocument binaryDocument;

    public BinaryDocumentXarSource(final BrokerPool pool, final Txn transaction, final BinaryDocument binaryDocument) {
        this.pool = pool;
        this.transaction = transaction;
        this.binaryDocument = binaryDocument;
    }

    @Override
    public URI getURI() {
        try {
            return new URI(binaryDocument.getURI().toString());
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public InputStream newInputStream() throws IOException {
        return pool.getBlobStore().get(transaction, binaryDocument.getBlobId());
    }
}

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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

import javax.xml.parsers.ParserConfigurationException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;

/**
 * @author Wolfgang Meier
 */
public class LocalXUpdateQueryService extends AbstractLocalService implements XUpdateQueryService {

	private final static Logger LOG = LogManager.getLogger(LocalXUpdateQueryService.class);

    private XUpdateProcessor processor = null;

    public LocalXUpdateQueryService(final Subject user, final BrokerPool pool, final LocalCollection parent) {
        super(user, pool, parent);
    }

    @Override
    public String getName() throws XMLDBException {
        return "XUpdateQueryService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public long updateResource(final String id, final String commands) throws XMLDBException {

        return this.<Long>withDb((broker, transaction) -> {

            final long start = System.currentTimeMillis();

            final MutableDocumentSet docs = this.<MutableDocumentSet>read(broker, transaction, collection.getPathURI()).apply((collection, broker1, transaction1) -> {
                MutableDocumentSet d = new DefaultDocumentSet();
                if (id == null) {
                    d = collection.allDocs(broker1, d, true);
                } else {
                    try {
                        final XmldbURI resourceURI = XmldbURI.xmldbUriFor(id);
                        try(final LockedDocument lockedDocument = collection.getDocumentWithLock(broker1, resourceURI, Lock.LockMode.READ_LOCK)) {

                            // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                            collection.close();

                            final DocumentImpl doc = lockedDocument == null ? null : lockedDocument.getDocument();
                            if (doc == null) {
                                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource not found: " + id);
                            }
                            d.add(doc);
                        }
                    } catch(final URISyntaxException e) {
                        throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
                    }
                }
                return d;
            });

            try(final Reader reader = new StringReader(commands)) {
                if (processor == null) {
                    processor = new XUpdateProcessor(broker, docs);
                } else {
                    processor.setBroker(broker);
                    processor.setDocumentSet(docs);
                }

                final Modification modifications[] = processor.parse(new InputSource(reader));
                long mods = 0;
                for (int i = 0; i < modifications.length; i++) {
                    mods += modifications[i].process(transaction);
                    broker.flush();
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("xupdate took " + (System.currentTimeMillis() - start) + "ms.");
                }

                return mods;
            } catch(final ParserConfigurationException | SAXException | LockException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(),e);
            } finally {
                if (processor != null) {
                    processor.reset();
                }
            }

        });
    }

    @Override
    public long update(final String commands) throws XMLDBException {
        return updateResource(null, commands);
    }

    @Override
    public String getProperty(final String name) throws XMLDBException {
        return null;
    }

    @Override
        public void setProperty(final String name, final String value) throws XMLDBException {
    }
}

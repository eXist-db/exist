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

import java.net.URISyntaxException;
import java.util.Date;
import java.util.Optional;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker.PreserveType;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class LocalCollectionManagementService extends AbstractLocalService implements EXistCollectionManagementService {

    public LocalCollectionManagementService(final Subject user, final BrokerPool pool, final LocalCollection parent) {
    	super(user, pool, parent);
    }

    @Override
    public String getName() throws XMLDBException {
        return "CollectionManagementService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public Collection createCollection(final String collName) throws XMLDBException {
        return createCollection(collName, (Date)null);
    }

    @Override
    public Collection createCollection(final XmldbURI collName) throws XMLDBException {
        return createCollection(collName, null);
    }

    @Override
    public Collection createCollection(final String collName, final Date created) throws XMLDBException {
    	try {
    		return createCollection(XmldbURI.xmldbUriFor(collName), created);
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }

    @Override
    public Collection createCollection(final XmldbURI name, final Date created) throws XMLDBException {
        final XmldbURI collName = resolve(name);

        withDb((broker, transaction) -> {
            try {
                final org.exist.collections.Collection coll = broker.getOrCreateCollection(transaction, collName, Optional.ofNullable(created).map(c -> new Tuple2<>(null, c.getTime())));
                try(final ManagedCollectionLock collectionLock = broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(collName)) {
                    broker.saveCollection(transaction, coll);
                }
                return null;
            } catch (final LockException | TriggerException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            }
        });

        return new LocalCollection(user, brokerPool, collection, collName);
    }

    @Override
    public String getProperty(final String property ) {
        return null;
    }

    @Override
    public void removeCollection(final String collName) throws XMLDBException {
    	try{
    		removeCollection(XmldbURI.xmldbUriFor(collName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }

    @Override
    public void removeCollection(final XmldbURI name) throws XMLDBException {
        final XmldbURI collName = resolve(name);
        modify(collName).apply((collection, broker, transaction) -> broker.removeCollection(transaction, collection));
    }

    @Override
    public void move(final String collectionPath, final String destinationPath, final String newName) throws XMLDBException {
    	try{
    		move(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }

    @Override
    public void move(final XmldbURI src, final XmldbURI dest, final XmldbURI name) throws XMLDBException {
    	final XmldbURI srcPath = resolve(src);
    	final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }

        withDb((broker, transaction) ->
                modify(broker, transaction, srcPath).apply((source, b1, t1) ->
                        modify(b1, t1, destPath).apply((destination, b2, t2) -> {
                            b2.moveCollection(t2, source, destination, newName);
                            return null;
                        })
                )
        );
    }

    @Override
    public void moveResource(final String resourcePath, final String destinationPath, final String newName) throws XMLDBException {
    	try{
    		moveResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }

    @Override
    public void moveResource(final XmldbURI src, final XmldbURI dest, final XmldbURI name) throws XMLDBException {
        final XmldbURI srcPath = resolve(src);
        final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }

        withDb((broker, transaction) ->
                modify(broker, transaction, srcPath.removeLastSegment()).apply((sourceCol, b1, t1) -> {
                    try(final LockedDocument lockedSource = sourceCol.getDocumentWithLock(b1, srcPath.lastSegment(), Lock.LockMode.WRITE_LOCK)) {
                        final DocumentImpl source = lockedSource == null ? null : lockedSource.getDocument();
                        if (source == null) {

                            // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                            sourceCol.close();

                            throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + srcPath + " not found");
                        }

                        return modify(b1, t1, destPath).apply((destinationCol, b2, t2) -> {
                            try(final ManagedDocumentLock lockedDestination = b2.getBrokerPool().getLockManager().acquireDocumentWriteLock(destinationCol.getURI().append(newName))) {

                                b2.moveResource(t2, source, destinationCol, newName);

                                // NOTE: early release of Collection locks inline with Asymmetrical Locking scheme
                                destinationCol.close();
                                sourceCol.close();
                            }

                            return null;
                        });
                    }
                })
        );
    }
    
    @Override
    public void copy(final String collectionPath, final String destinationPath, final String newName) throws XMLDBException {
    	try{
    		copy(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName), PreserveType.DEFAULT);
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }

    @Override
    public void copy(final XmldbURI src, final XmldbURI dest, final XmldbURI name) throws XMLDBException {
        copy(src, dest, name, PreserveType.DEFAULT);
    }

    @Override
    public void copy(final XmldbURI src, final XmldbURI dest, final XmldbURI name, final String preserveType) throws XMLDBException {
        copy(src, dest, name, PreserveType.valueOf(preserveType));
    }

    private void copy(final XmldbURI src, final XmldbURI dest, final XmldbURI name, final PreserveType preserve) throws XMLDBException {
        final XmldbURI srcPath = resolve(src);
        final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }

        withDb((broker, transaction) ->
                read(broker, transaction, srcPath).apply((source, b1, t1) ->
                        modify(b1, t1, destPath).apply((destination, b2, t2) -> {
                            try {
                                b2.copyCollection(t2, source, destination, newName, preserve);
                                return null;
                            } catch (final EXistException e) {
                                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "failed to move collection " + srcPath, e);
                            }
                        })
                )
        );
    }

    @Override
    public void copyResource(final String resourcePath, final String destinationPath, final String newName) throws XMLDBException {
    	try{
    		copyResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName), PreserveType.DEFAULT);
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }

    @Override
    public void copyResource(final XmldbURI src, final XmldbURI dest, final XmldbURI name) throws XMLDBException {
        copyResource(src, dest, name, PreserveType.DEFAULT);
    }

    @Override
    public void copyResource(final XmldbURI src, final XmldbURI dest, final XmldbURI name, final String preserveType) throws XMLDBException {
        copyResource(src, dest, name, PreserveType.valueOf(preserveType));
    }

    private void copyResource(final XmldbURI src, final XmldbURI dest, final XmldbURI name, final PreserveType preserve) throws XMLDBException {
        final XmldbURI srcPath = resolve(src);
        final XmldbURI destPath = dest == null ? srcPath.removeLastSegment() : resolve(dest);
        final XmldbURI newName;
        if (name == null) {
            newName = srcPath.lastSegment();
        } else {
            newName = name;
        }

        withDb((broker, transaction) ->
            read(broker, transaction, srcPath.removeLastSegment()).apply((sourceCol, b1, t1) -> {
                try(final LockedDocument lockedSource = sourceCol.getDocumentWithLock(b1, srcPath.lastSegment(), Lock.LockMode.READ_LOCK)) {
                    final DocumentImpl source = lockedSource == null ? null : lockedSource.getDocument();
                    if (source == null) {

                        // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                        sourceCol.close();

                        throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + srcPath + " not found");
                    }

                    return modify(b1, t1, destPath).apply((destinationCol, b2, t2) -> {
                        try(final ManagedDocumentLock lockedDestination = b2.getBrokerPool().getLockManager().acquireDocumentWriteLock(destinationCol.getURI().append(newName))) {
                            try {
                                b2.copyResource(t2, source, destinationCol, newName, preserve);

                                // NOTE: early release of Collection locks inline with Asymmetrical Locking scheme
                                destinationCol.close();
                                sourceCol.close();

                                return null;
                            } catch (final EXistException e) {
                                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "failed to copy resource " + srcPath, e);
                            }
                        }
                    });
                }
            })
        );
    }

    @Override
    public void setProperty(final String property, final String value) {
    }
	
    @Override
    public void runCommand(final String[] params) throws XMLDBException {
    	withDb((broker, transaction) -> {
            org.exist.plugin.command.Commands.command(XmldbURI.create(collection.getPath()), params);
            return null;
        });
    }
}


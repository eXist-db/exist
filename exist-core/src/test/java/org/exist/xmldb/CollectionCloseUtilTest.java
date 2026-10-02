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
package org.exist.xmldb;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class CollectionCloseUtilTest {

    @Test
    void doesNotCloseWhenCandidateIsTheBorrowedCollection() {
        final StubCollection borrowed = new StubCollection();
        CollectionCloseUtil.closeIfOwn(borrowed, borrowed, e -> fail("unexpected error: " + e));
        assertEquals(0, borrowed.closeCount.get());
    }

    @Test
    void doesNotCloseWhenCandidateIsNull() {
        final StubCollection borrowed = new StubCollection();
        // must not throw NPE, and must not report an error, for a null candidate
        assertDoesNotThrow(() ->
                CollectionCloseUtil.closeIfOwn(borrowed, null, e -> fail("unexpected error: " + e)));
    }

    @Test
    void closesADistinctCandidate() {
        final StubCollection borrowed = new StubCollection();
        final StubCollection candidate = new StubCollection();
        CollectionCloseUtil.closeIfOwn(borrowed, candidate, e -> fail("unexpected error: " + e));
        assertEquals(1, candidate.closeCount.get());
        assertEquals(0, borrowed.closeCount.get());
    }

    @Test
    void reportsACloseFailureInsteadOfThrowing() {
        final StubCollection borrowed = new StubCollection();
        final StubCollection candidate = new StubCollection();
        final XMLDBException closeFailure = new XMLDBException(ErrorCodes.VENDOR_ERROR, "close failed");
        candidate.closeException = closeFailure;

        final AtomicInteger reportedErrors = new AtomicInteger();
        CollectionCloseUtil.closeIfOwn(borrowed, candidate, e -> {
            assertSame(closeFailure, e);
            reportedErrors.incrementAndGet();
        });

        assertEquals(1, reportedErrors.get());
        assertEquals(1, candidate.closeCount.get());
    }

    /**
     * Minimal {@link Collection} stub. Only {@link #close()} is meaningful --
     * {@link CollectionCloseUtil#closeIfOwn} never calls anything else.
     */
    private static class StubCollection implements Collection {
        final AtomicInteger closeCount = new AtomicInteger();
        XMLDBException closeException;

        @Override
        public void close() throws XMLDBException {
            closeCount.incrementAndGet();
            if (closeException != null) {
                throw closeException;
            }
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection getParentCollection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getChildCollectionCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> listChildCollections() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection getChildCollection(final String collectionName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getResourceCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> listResources() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <R extends Resource> R createResource(final String id, final Class<R> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeResource(final Resource res) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void storeResource(final Resource res) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Resource getResource(final String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String createId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant getCreationTime() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getProperty(final String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getProperty(final String name, final String defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setProperty(final String name, final String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Service> boolean hasService(final Class<S> serviceType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Service> Optional<S> findService(final Class<S> serviceType) {
            throw new UnsupportedOperationException();
        }
    }
}

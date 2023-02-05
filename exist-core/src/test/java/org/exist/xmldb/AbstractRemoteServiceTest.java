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

import org.easymock.EasyMockExtension;
import org.easymock.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xmldb.api.base.XMLDBException;

import static org.assertj.core.api.Assertions.*;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.xmldb.api.base.ErrorCodes.INVALID_COLLECTION;

@ExtendWith(EasyMockExtension.class)
class AbstractRemoteServiceTest {
    @Mock
    RemoteCollection collection;
    TestRemoteService service;

    @BeforeEach
    void prepare() {
        replay(collection);
        service = new TestRemoteService(collection);
    }

    @Test
    void setCollection() {
        assertThatNoException()
                .isThrownBy(() -> service.setCollection(collection));
    }

    @Test
    void setWrongCollectionType() {
        LocalCollection localCollection = createMock(LocalCollection.class);
        assertThatExceptionOfType(XMLDBException.class)
                .isThrownBy(() -> service.setCollection(localCollection))
                .withMessage("incompatible collection type: " + localCollection.getClass().getName())
                .satisfies(ex -> assertThat(ex.errorCode).isEqualTo(INVALID_COLLECTION));
    }

    @Test
    void getProperty() throws XMLDBException {
        assertThat(service.getProperty("test1")).isNull();
    }

    @Test
    void getPropertyWithDefaultValue() throws XMLDBException {
        assertThat(service.getProperty("test2", "defaultValue")).isEqualTo("defaultValue");
    }

    @Test
    void setProperty() {
        assertThatNoException().isThrownBy(() -> service.setProperty("test3", "someValue"));
    }

    static class TestRemoteService extends AbstractRemoteService {
        TestRemoteService(RemoteCollection collection) {
            super(collection);
        }

        @Override
        public String getName() throws XMLDBException {
            return null;
        }

        @Override
        public String getVersion() throws XMLDBException {
            return null;
        }
    }
}
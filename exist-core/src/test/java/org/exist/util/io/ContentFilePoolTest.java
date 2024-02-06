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

package org.exist.util.io;

import org.easymock.EasyMockExtension;
import org.easymock.Mock;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.*;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.exist.util.io.ContentFilePool.PROPERTY_IN_MEMORY_SIZE;
import static org.exist.util.io.ContentFilePool.PROPERTY_POOL_SIZE;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
@ExtendWith(EasyMockExtension.class)
class ContentFilePoolTest {
    @Mock
    ContentFile contentFile;
    @Mock
    TemporaryFileManager temporaryFileManager;
    Configuration configuration;
    ContentFilePool pool;

    @BeforeEach
    void prepare() throws DatabaseConfigurationException {
        configuration = new Configuration();
        configuration.setProperty(PROPERTY_POOL_SIZE, 1);
        configuration.setProperty(PROPERTY_IN_MEMORY_SIZE, 10);
        pool = new ContentFilePool(temporaryFileManager, configuration);
        replay(contentFile, temporaryFileManager);
    }

    @AfterEach
    void verifyMocks() {
        verify(contentFile, temporaryFileManager);
    }

    @Test
    void testReturnNullObject() {
        assertThatNoException().isThrownBy(()-> pool.returnObject(null));
    }

    @Test
    void testBorrowAndReturnObject() {
        final ContentFile contentFile = pool.borrowObject();
        assertThat(contentFile).isInstanceOf(VirtualTempPath.class);
        assertThatNoException().isThrownBy(()-> pool.returnObject(contentFile));
    }

    @Test
    void testBorrowExhausted() {
        assertThat(pool.borrowObject()).isNotNull();
        assertThatIllegalStateException().isThrownBy(pool::borrowObject);
    }

    @Test
    void testReturnNonBorrowedObject() {
        reset(contentFile);
        contentFile.close();
        replay(contentFile);
        assertThatIllegalStateException().isThrownBy(() -> pool.returnObject(contentFile));
    }
}
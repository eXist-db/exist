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
package org.exist.xmlrpc;

import org.easymock.EasyMockExtension;
import org.easymock.Mock;
import org.exist.util.io.ContentFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.function.Consumer;

import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.assertj.core.api.Assertions.*;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
@ExtendWith(EasyMockExtension.class)
class CachedContentFileTest {
    @Mock
    ContentFile contentFile;
    @Mock
    Consumer<ContentFile> contentFileConsumer;

    CachedContentFile cachedContentFile;
    CachedContentFile cachedContentFileNoContent;

    @BeforeEach
    void prepare() {
        cachedContentFile = new CachedContentFile(contentFile, contentFileConsumer);
        cachedContentFileNoContent= new CachedContentFile(null, contentFileConsumer);
    }

    @AfterEach
    void verifyMocks() {
        verify(contentFile, contentFileConsumer);
    }

    @Test
    void testGetResult() {
        replay(contentFile, contentFileConsumer);
        assertThat(cachedContentFile.getResult()).isEqualTo(contentFile);
        assertThat(cachedContentFileNoContent.getResult()).isNull();
    }

    @Test
    void testDoClose() {
        contentFile.close();
        contentFileConsumer.accept(contentFile);
        replay(contentFile, contentFileConsumer);
        assertThatNoException().isThrownBy(cachedContentFile::doClose);
        assertThatNoException().isThrownBy(cachedContentFileNoContent::doClose);
    }
}
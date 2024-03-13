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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
class QueryResultCacheTest {
    QueryResultCache cache = new QueryResultCache();
    TestCachedResult cachedResult = new TestCachedResult();

    @BeforeEach
    void prepare() {
        assertThat(cache.add(cachedResult)).isZero();
        assertThat(cachedResult.getResult()).isZero();
    }

    @Test
    void testGet() {
        assertThat(cache.get(-1)).isNull();
        assertThat(cache.get(0)).isSameAs(cachedResult);
        assertThat(cache.get(1)).isNull();
    }

    @Test
    void testGetResult() {
        assertThat(cache.getResult(-1)).isNull();
        assertThat(cache.getResult(0)).isNull();
        assertThat(cache.getResult(1)).isNull();
    }

    @Test
    void testGetSerializedResult() {
        assertThat(cache.getSerializedResult(-1)).isNull();
        assertThat(cache.getSerializedResult(0)).isNull();
        assertThat(cache.getSerializedResult(1)).isNull();
    }

    @Test
    void testGetCachedContentFile() {
        assertThat(cache.getCachedContentFile(-1)).isNull();
        assertThat(cache.getCachedContentFile(0)).isNull();
        assertThat(cache.getCachedContentFile(1)).isNull();
    }

    @Test
    void testRemove() throws InterruptedException {
        assertThatNoException().isThrownBy(() ->  cache.remove(-1));
        assertThatNoException().isThrownBy(() ->  cache.remove(0));

        await().atMost(1, SECONDS).untilAsserted(() -> assertThat(cachedResult.getResult()).isOne());
        assertThatNoException().isThrownBy(() ->  cache.remove(1));
    }

    @Test
    void testRemoveWithHashCode() throws InterruptedException {
        assertThatNoException().isThrownBy(() ->  cache.remove(-1, 0));
        assertThatNoException().isThrownBy(() ->  cache.remove(0, 0));
        assertThat(cachedResult.getResult()).isZero();
        assertThatNoException().isThrownBy(() ->  cache.remove(0, 42));

        await().atMost(1, SECONDS).untilAsserted(() -> assertThat(cachedResult.getResult()).isOne());
        assertThatNoException().isThrownBy(() ->  cache.remove(1, 0));
    }

    static class TestCachedResult extends AbstractCachedResult {
        private int closeCount;

        @Override
        public Integer getResult() {
            return Integer.valueOf(closeCount);
        }

        @Override
        protected void doClose() {
            closeCount++;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }
}
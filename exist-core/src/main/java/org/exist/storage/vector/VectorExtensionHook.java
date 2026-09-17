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
package org.exist.storage.vector;

import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.util.Configuration;

/**
 * SPI for the optional {@code extensions/vector} module to hook into broker pool
 * configuration and lifecycle, without exist-core taking a compile-time dependency
 * on that (optional) extension module.
 *
 * <p>Register an implementation's fully-qualified class name in
 * {@code META-INF/services/org.exist.storage.vector.VectorExtensionHook}. At startup,
 * {@link VectorStoreServiceImpl} discovers it via {@link java.util.ServiceLoader};
 * if the vector extension is not on the classpath, no implementation is found and the
 * hooks are simply skipped — mirroring how {@link org.exist.xquery.ModuleFactory} and
 * {@link org.exist.indexing.IndexFactory} let optional modules/indexes self-register.
 *
 * <p><strong>Design note:</strong> this interface's three methods deliberately mirror three
 * of {@link BrokerPoolService}'s own lifecycle hooks. {@code BrokerPoolService} already solves
 * "run code at these points in startup/shutdown" for every built-in service; the reason this
 * SPI exists as a separate, narrower interface rather than exist-core discovering
 * {@code BrokerPoolService} implementations via {@code ServiceLoader} directly is that the
 * latter would be a broader change to core startup sequencing (ordering/failure semantics for
 * every service, not just an optional one), which was out of scope when this was introduced
 * purely to replace ad hoc reflection in {@link VectorStoreServiceImpl}. If a second optional
 * extension ever needs the same "hook into core lifecycle without a core-to-extension compile
 * dependency" trick, that repetition is the signal to stop adding one-off SPIs like this one
 * and instead make {@code BrokerPoolService} registration itself {@code ServiceLoader}-based.
 */
public interface VectorExtensionHook {

    /**
     * Called during broker pool configuration.
     *
     * @param configuration the eXist-db configuration
     */
    default void configure(Configuration configuration) {
        // no-op default: an implementation only needs to override the hooks it cares about
    }

    /**
     * Called when the broker pool starts the system broker.
     *
     * @param pool the broker pool
     */
    default void startSystem(BrokerPool pool) {
        // no-op default: an implementation only needs to override the hooks it cares about
    }

    /**
     * Called when the broker pool shuts down.
     *
     * @param pool the broker pool
     */
    default void shutdown(BrokerPool pool) {
        // no-op default: an implementation only needs to override the hooks it cares about
    }
}

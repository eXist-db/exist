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
package org.exist.launcher;

import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import com.evolvedbinary.j8fu.lazy.AtomicLazyValE;
import org.apache.commons.lang3.SystemUtils;

import javax.annotation.Nullable;

public class ServiceManagerFactory {

    private static final AtomicLazyVal<ServiceManager> WINDOWS_SERVICE_MANAGER = new AtomicLazyVal<>(WindowsServiceManager::new);

    /**
     * Returns the service manager for the current
     * platform or null if the platform is unsupported.
     *
     * @return the service manager, or null if the platform is unsupported.
     */
    public static @Nullable ServiceManager getServiceManager() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return WINDOWS_SERVICE_MANAGER.get();
        }

        return null;
    }
}

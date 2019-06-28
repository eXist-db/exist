/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import java.util.List;

/**
 * Exception reported by BrokerPoolServicesManager
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class BrokerPoolServicesManagerException extends Exception {
    private final List<BrokerPoolServiceException> serviceExceptions;

    public BrokerPoolServicesManagerException(final List<BrokerPoolServiceException> serviceExceptions) {
        this.serviceExceptions = serviceExceptions;
    }

    public /*@Nullable*/ List<BrokerPoolServiceException> getServiceExceptions() {
        return serviceExceptions;
    }
}

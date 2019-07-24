/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
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
package org.exist.storage;

import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;

/**
 * Interface for a class which provides
 * services to a BrokerPool instance
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public interface BrokerPoolService {

    /**
     * Configure this service
     *
     * By default there is nothing to configure.
     *
     * @param configuration BrokerPool configuration
     *
     * @throws BrokerPoolServiceException if an error occurs when configuring the service
     */
    default void configure(final Configuration configuration) throws BrokerPoolServiceException {
        //nothing to configure
    }

    /**
     * Prepare this service
     *
     * Prepare is called before the BrokerPool enters
     * system (single user) mode. As yet there are still
     * no brokers
     *
     * @param brokerPool The BrokerPool instance that is being prepared
     *
     * @throws BrokerPoolServiceException if an error occurs when preparing the service
     */
    default void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        //nothing to prepare
    }

    /**
     * Start any part of this service that should happen during
     * system (single-user) mode.
     *
     * As this point the database is not generally available
     * and the only system broker is passed to this function
     *
     * @param systemBroker The system mode broker
     * @param transaction The transaction for the system service
     *
     * @throws BrokerPoolServiceException if an error occurs when starting the system service
     */
    default void startSystem(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        // nothing to start
    }

    /**
     * Start any part of this service that should happen at the
     * end of system (single-user) mode and directly before multi-user
     * mode
     *
     * As this point the database is not generally available,
     * {@link #startSystem(DBBroker, Txn)} has already been called
     * for all services, any reindexing and recovery has completed
     * but there is still only a system broker which is passed to this
     * function
     *
     * @param systemBroker The system mode broker
     * @param transaction The transaction for the pre-multi-user system service
     *
     * @throws BrokerPoolServiceException if an error occurs when starting the pre-multi-user system service
     */
    default void startPreMultiUserSystem(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        //nothing to start
    }

    /**
     * Start any part of this service that should happen at the
     * start of multi-user mode
     *
     * As this point the database is generally available,
     * {@link #startPreMultiUserSystem(DBBroker, Txn)} has already been called
     * for all services. You may be competing with other services and/or
     * users for database access
     *
     * @param brokerPool The multi-user available broker pool instance
     *
     * @throws BrokerPoolServiceException if an error occurs when starting the multi-user service
     */
    default void startMultiUser(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        //nothing to start
    }

    /**
     * Stop this service.
     *
     * By default there is nothing to stop
     *
     * As this point the database is not generally available
     * and the only system broker is passed to this function
     *
     * @param systemBroker The system mode broker
     *
     * @throws BrokerPoolServiceException if an error occurs when stopping the service
     */
    default void stop(final DBBroker systemBroker) throws BrokerPoolServiceException {
        //nothing to actually stop
    }

    /**
     * Shutdown this service.
     *
     * By default there is nothing to shutdown
     */
    default void shutdown() {
        //nothing to actually shutdown
    }
}

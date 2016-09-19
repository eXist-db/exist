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

import net.jcip.annotations.NotThreadSafe;
import org.exist.util.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * This class simply maintains a list of {@link BrokerPoolService}
 * and provides methods to {@BrokerPool} to manage the lifecycle of
 * those services.
 *
 * This class should only be accessed from {@link BrokerPool}
 * and the order of method invocation (service state change)
 * is significant and must follow the order:
 *
 *      register -> configure -> prepare ->
 *          system -> pre-multi-user -> multi-user
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
@NotThreadSafe
class BrokerPoolServicesManager {

    private enum ManagerState {
        REGISTRATION,
        CONFIGURATION,
        PREPARATION,
        SYSTEM,
        PRE_MULTI_USER,
        MULTI_USER
    }

    private ManagerState state = ManagerState.REGISTRATION;

    final List<BrokerPoolService> brokerPoolServices = new ArrayList<>();

    /**
     * Register a Service to be managed
     *
     * Note all services must be registered before any service is configured
     * failure to do so will result in an {@link IllegalStateException}
     *
     * @param brokerPoolService The service to be managed
     *
     * @return The service after it has been registered
     *
     * @throws IllegalStateException Thrown if there is an attempt to register a service
     * after any other service has been configured.
     */
    <T extends BrokerPoolService> T register(final T brokerPoolService) {
        if(state != ManagerState.REGISTRATION) {
            throw new IllegalStateException(
                    "Services may only be registered during the registration state. Current state is: " + state.name());
        }

        brokerPoolServices.add(brokerPoolService);
        return brokerPoolService;
    }

    /**
     * Configures the Services
     *
     * Expected to be called from {@link BrokerPool#initialize()}
     *
     * @param configuration The database configuration (i.e. conf.xml)
     *
     * @throws BrokerPoolServiceException if any service causes an error during configuration
     *
     * @throws IllegalStateException Thrown if there is an attempt to configure a service
     * after any other service has been prepared.
     */
    void configureServices(final Configuration configuration) throws BrokerPoolServiceException {
        if(state != ManagerState.REGISTRATION) {
            throw new IllegalStateException(
                    "Services may only be configured after the registration state. Current state is: " + state.name());
        } else {
            state = ManagerState.CONFIGURATION;
        }

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.configure(configuration);
        }
    }

    /**
     * Prepare the Services for system (single user) mode
     *
     * Prepare is called before the BrokerPool enters
     * system (single user) mode. As yet there are still
     * no brokers!
     *
     * @throws BrokerPoolServiceException if any service causes an error during preparation
     *
     * @throws IllegalStateException Thrown if there is an attempt to prepare a service
     * after any other service has entered start system service.
     */
    void prepareServices(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        if(state != ManagerState.CONFIGURATION) {
            throw new IllegalStateException(
                    "Services may only be prepared after the configuration state. Current state is: " + state.name());
        } else {
            state = ManagerState.PREPARATION;
        }

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.prepare(brokerPool);
        }
    }

    /**
     * Starts any services which should be started directly after
     * the database enters system mode, but before any system mode
     * operations are performed.
     *
     * At this point the broker pool is in system (single user) mode
     * and not generally available for access, only a single
     * system broker is available.
     *
     * @param systemBroker The System Broker which is available for
     *   services to use to access the database
     *
     * @throws BrokerPoolServiceException if any service causes an error during starting the system mode
     *
     * @throws IllegalStateException Thrown if there is an attempt to start a service
     * after any other service has entered the start pre-multi-user system mode.
     */
    void startSystemServices(final DBBroker systemBroker) throws BrokerPoolServiceException {
        if(state != ManagerState.PREPARATION) {
            throw new IllegalStateException(
                    "Services may only be started in system mode after the preparation state. Current state is: "
                            + state.name());
        } else {
            this.state = ManagerState.SYSTEM;
        }

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.startSystem(systemBroker);
        }
    }

    /**
     * Starts any services which should be started directly after
     * the database finishes system mode operations, but before
     * entering multi-user mode
     *
     * At this point the broker pool is still in system (single user) mode
     * and not generally available for access, only a single
     * system broker is available.
     *
     * @param systemBroker The System Broker which is available for
     *   services to use to access the database
     *
     * @throws BrokerPoolServiceException if any service causes an error during starting the pre-multi-user mode
     *
     * @throws IllegalStateException Thrown if there is an attempt to start pre-multi-user system a service
     * after any other service has entered multi-user.
     */
    void startPreMultiUserSystemServices(final DBBroker systemBroker) throws BrokerPoolServiceException {
        if(state != ManagerState.SYSTEM) {
            throw new IllegalStateException(
                    "Services may only be started in pre-multi-user mode after the system state. Current state is: "
                            + state.name());
        } else {
            this.state = ManagerState.PRE_MULTI_USER;
        }

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.startPreMultiUserSystem(systemBroker);
        }
    }

    /**
     * Starts any services which should be started once the database
     * enters multi-user mode
     *
     * @param brokerPool The broker pool instance
     *
     * @throws BrokerPoolServiceException if any service causes an error during starting multi-user mode
     *
     * @throws IllegalStateException Thrown if there is an attempt to start multi-user a service
     * before we have completed pre-multi-user mode
     */
    void startMultiUserServices(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        if(state != ManagerState.PRE_MULTI_USER) {
            throw new IllegalStateException(
                    "Services may only be started in pre-multi-user mode after the system state. Current state is: "
                            + state.name());
        } else {
            this.state = ManagerState.MULTI_USER;
        }

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.startMultiUser(brokerPool);
        }
    }
}

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
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import com.evolvedbinary.j8fu.fsm.AtomicFSM;
import com.evolvedbinary.j8fu.fsm.FSM;

import static com.evolvedbinary.j8fu.fsm.TransitionTable.transitionTable;
import static org.exist.security.UnixStylePermission.LOG;


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
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@NotThreadSafe
class BrokerPoolServicesManager {

    private enum ManagerState {
        REGISTRATION,
        CONFIGURATION,
        PREPARATION,
        SYSTEM,
        PRE_MULTI_USER,
        MULTI_USER,
        STOPPING,
        SHUTTING_DOWN
    }

    private enum ManagerEvent {
        CONFIGURE,
        PREPARE,
        ENTER_SYSTEM_MODE,
        PREPARE_ENTER_MULTI_USER_MODE,
        ENTER_MULTI_USER_MODE,
        STOP,
        SHUTDOWN
    }

    @SuppressWarnings("unchecked")
    private FSM<ManagerState, ManagerEvent> states = new AtomicFSM<>(ManagerState.REGISTRATION, transitionTable(ManagerState.class, ManagerEvent.class)
            .when(ManagerState.REGISTRATION)
                .on(ManagerEvent.CONFIGURE).switchTo(ManagerState.CONFIGURATION)
                .on(ManagerEvent.PREPARE).switchTo(ManagerState.PREPARATION)
                .on(ManagerEvent.ENTER_SYSTEM_MODE).switchTo(ManagerState.SYSTEM)
                .on(ManagerEvent.PREPARE_ENTER_MULTI_USER_MODE).switchTo(ManagerState.PRE_MULTI_USER)
                .on(ManagerEvent.ENTER_MULTI_USER_MODE).switchTo(ManagerState.MULTI_USER)
                .on(ManagerEvent.STOP).switchTo(ManagerState.STOPPING)
                .on(ManagerEvent.SHUTDOWN).switchTo(ManagerState.SHUTTING_DOWN)
            .build()
    );

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
        final ManagerState currentState = states.getCurrentState();
        if(currentState != ManagerState.REGISTRATION) {
            throw new IllegalStateException(
                    "Services may only be registered during the registration state. Current state is: " + currentState.name());
        }

        brokerPoolServices.add(brokerPoolService);
        if(LOG.isTraceEnabled()) {
            LOG.trace("Registered service: " + brokerPoolService.getClass().getSimpleName() + "...");
        }
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
        states.process(ManagerEvent.CONFIGURE);

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            if(LOG.isTraceEnabled()) {
                LOG.trace("Configuring service: " + brokerPoolService.getClass().getSimpleName() + "...");
            }
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
        states.process(ManagerEvent.PREPARE);

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            if(LOG.isTraceEnabled()) {
                LOG.trace("Preparing service: " + brokerPoolService.getClass().getSimpleName() + "...");
            }
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
     * @param transaction The transaction for the system services
     *
     * @throws BrokerPoolServiceException if any service causes an error during starting the system mode
     *
     * @throws IllegalStateException Thrown if there is an attempt to start a service
     * after any other service has entered the start pre-multi-user system mode.
     */
    void startSystemServices(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        states.process(ManagerEvent.ENTER_SYSTEM_MODE);

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            if(LOG.isTraceEnabled()) {
                LOG.trace("Notifying service: " + brokerPoolService.getClass().getSimpleName() + " of start system...");
            }
            brokerPoolService.startSystem(systemBroker, transaction);
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
     * @param transaction The transaction for the pre-multi-user system services
     *
     * @throws BrokerPoolServiceException if any service causes an error during starting the pre-multi-user mode
     *
     * @throws IllegalStateException Thrown if there is an attempt to start pre-multi-user system a service
     * after any other service has entered multi-user.
     */
    void startPreMultiUserSystemServices(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        states.process(ManagerEvent.PREPARE_ENTER_MULTI_USER_MODE);

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            if(LOG.isTraceEnabled()) {
                LOG.trace("Notifying service: " + brokerPoolService.getClass().getSimpleName() + " of start pre-multi-user...");
            }
            brokerPoolService.startPreMultiUserSystem(systemBroker, transaction);
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
        states.process(ManagerEvent.ENTER_MULTI_USER_MODE);

        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            if(LOG.isTraceEnabled()) {
                LOG.trace("Notifying service: " + brokerPoolService.getClass().getSimpleName() + " of start multi-user...");
            }
            brokerPoolService.startMultiUser(brokerPool);
        }
    }

    /**
     * Stops any services which were previously started.
     *
     * At this point the broker pool is likely back in system (single user) mode
     * and not generally available for access, only a single
     * system broker is available.
     *
     * @param systemBroker The System Broker which is available for
     *   services to use to access the database
     *
     * @throws BrokerPoolServiceException if any service causes an error when stopping
     *
     * @throws IllegalStateException Thrown if there is an attempt to stop a service
     * before we have completed starting multi-user mode
     */
    void stopServices(final DBBroker systemBroker) throws BrokerPoolServicesManagerException {
        states.process(ManagerEvent.STOP);

        List<BrokerPoolServiceException> serviceExceptions = null;

        // we stop in the reverse order to starting up
        for(int i = brokerPoolServices.size() - 1; i >= 0; i--) {
            final BrokerPoolService brokerPoolService = brokerPoolServices.get(i);
            if(LOG.isTraceEnabled()) {
                LOG.trace("Stopping service: " + brokerPoolService.getClass().getSimpleName() + "...");
            }

            try {
                brokerPoolService.stop(systemBroker);
            } catch (final BrokerPoolServiceException e) {
                if(serviceExceptions == null) {
                    serviceExceptions = new ArrayList<>();
                }
                serviceExceptions.add(e);
            }
        }

        if(serviceExceptions != null) {
            throw new BrokerPoolServicesManagerException(serviceExceptions);
        }
    }

    /**
     * Shutdown any services which were previously configured.
     *
     * @throws IllegalStateException Thrown if there is an attempt to shutdown a service
     * before we have completed stopping services
     */
    void shutdown() {
        states.process(ManagerEvent.SHUTDOWN);

        // we shutdown in the reverse order to starting up
        for(int i = brokerPoolServices.size() - 1; i >= 0; i--) {
            final BrokerPoolService brokerPoolService = brokerPoolServices.get(i);
            if(LOG.isTraceEnabled()) {
                LOG.trace("Shutting down service: " + brokerPoolService.getClass().getSimpleName() + "...");
            }
            brokerPoolService.shutdown();
        }
    }
}

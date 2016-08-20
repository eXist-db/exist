package org.exist.storage;

import org.exist.util.Configuration;

/**
 * Created by aretter on 20/08/2016.
 */
public interface BrokerPoolService {

    /**
     * Configure this service
     *
     * By default there is nothing to configure.
     *
     * @param configuration BrokerPool configuration
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
     */
    default void startSystem(final DBBroker systemBroker) throws BrokerPoolServiceException {
        // nothing to start
    }

    /**
     * Start any part of this service that should happen at the
     * end of system (single-user) mode and directly before multi-user
     * mode
     *
     * As this point the database is not generally available,
     * {@link #startSystem(DBBroker)} has already been called
     * for all services, any reindexing and recovery has completed
     * but there is still only a system broker which is passed to this
     * function
     */
    default void startTrailingSystem(final DBBroker systemBroker) throws BrokerPoolServiceException {
        //nothing to start
    }

    /**
     * Stop this service
     *
     * By default there is nothing to stop
     *
     * @param brokerPool The BrokerPool instance that is stopping
     */
    default void stop(final BrokerPool brokerPool) {
        //nothing to actually stop
    }
}

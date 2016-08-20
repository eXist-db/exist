package org.exist.storage;

import org.exist.util.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by aretter on 20/08/2016.
 */
class BrokerPoolServicesManager {

    final List<BrokerPoolService> brokerPoolServices = new ArrayList<>();

//    public <T extends BrokerPoolService> T register(final Function<BrokerPool, T> cstr) {
//        return brokerPoolServices.add(cstr.apply(brokerPool));
//    }

    <T extends BrokerPoolService> T register(final T brokerPoolService) {
        brokerPoolServices.add(brokerPoolService);
        return brokerPoolService;
    }

    void configureServices(final Configuration configuration) throws BrokerPoolServiceException {
        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.configure(configuration);
        }
    }

    void prepareServices(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        //TODO(AR) perhaps... create a proxy around BrokerPool which masks off things like getBroker so we can
        //avoid people doing things they shouldn't at this stage... or instead of a proxy
        //create an interface for BrokerPool which we use here instead and only provides getId and maybe a couple other things?
        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.prepare(brokerPool);
        }
    }

    void startSystemServices(final DBBroker systemBroker) throws BrokerPoolServiceException {
        //TODO(AR) consider how we could prevent users calling systemBroker.getBrokerPool.getBroker
        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.startSystem(systemBroker);
        }
    }

    void startTrailingSystemServices(final DBBroker systemBroker) throws BrokerPoolServiceException {
        //TODO(AR) consider how we could prevent users calling systemBroker.getBrokerPool.getBroker
        for(final BrokerPoolService brokerPoolService : brokerPoolServices) {
            brokerPoolService.startTrailingSystem(systemBroker);
        }
    }
}

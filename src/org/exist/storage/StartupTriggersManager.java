package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.plugin.PluginsManagerImpl;
import org.exist.util.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by aretter on 20/08/2016.
 */
public class StartupTriggersManager implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(PluginsManagerImpl.class);

    private final List<Configuration.StartupTriggerConfig> startupTriggerConfigs = new ArrayList<>();

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        final List<Configuration.StartupTriggerConfig> startupTriggerConfigs =
                (List<Configuration.StartupTriggerConfig>) configuration
                        .getProperty(BrokerPool.PROPERTY_STARTUP_TRIGGERS);
        if(startupTriggerConfigs != null) {
            this.startupTriggerConfigs.addAll(startupTriggerConfigs);
        }
    }

    @Override
    public void startTrailingSystem(final DBBroker systemBroker) throws BrokerPoolServiceException {
        for(final Configuration.StartupTriggerConfig startupTriggerConfig : startupTriggerConfigs) {
            try {
                final Class<StartupTrigger> clazz = (Class<StartupTrigger>) Class.forName(startupTriggerConfig.getClazz());
                final StartupTrigger startupTrigger = clazz.newInstance();
                startupTrigger.execute(systemBroker, startupTriggerConfig.getParams());
            } catch(final ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                LOG.error("Could not call StartupTrigger class: " + startupTriggerConfig + ". SKIPPING! " + e.getMessage(), e);
            } catch(final RuntimeException re) {
                LOG.warn("StartupTrigger threw RuntimeException: " + re.getMessage() + ". IGNORING!", re);
            }
        }
    }
}

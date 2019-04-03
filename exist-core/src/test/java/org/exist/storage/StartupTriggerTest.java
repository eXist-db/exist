package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for startup triggers.
 */
public class StartupTriggerTest {

    private final static String USER = "testuser1";
    private final static String PASSWORD = "testpass";

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(null, null, properties(), true, true);

    public static Properties properties() {
        final List<Configuration.StartupTriggerConfig> startupTriggers = new ArrayList<>();
        startupTriggers.add(new Configuration.StartupTriggerConfig(TestStartupTrigger.class.getName(), null));

        final Properties properties = new Properties();
        properties.put(BrokerPool.PROPERTY_STARTUP_TRIGGERS, startupTriggers);
        return properties;
    }

    /**
     * Check if startup trigger has access to security manager.
     */
    @Test
    public void createUser() throws DatabaseConfigurationException, EXistException, IOException {
        assertTrue(TestStartupTrigger.completed);
    }

    public static class TestStartupTrigger implements StartupTrigger {

        static volatile boolean completed = false;

        @Override
        public void execute(final DBBroker sysBroker, final Txn transaction, final Map<String, List<? extends Object>> params) {
            final SecurityManager secman = sysBroker.getBrokerPool().getSecurityManager();
            if (!secman.hasAccount(USER)) {
                final UserAider aider = new UserAider(USER);
                aider.setPassword(PASSWORD);

                try {
                    secman.addAccount(sysBroker, aider);
                } catch (final PermissionDeniedException | EXistException e) {
                    fail(e.getMessage());
                }

                assertTrue(secman.hasAccount(USER));

                try {
                    secman.deleteAccount(USER);
                } catch (final PermissionDeniedException | EXistException e) {
                    fail(e.getMessage());
                }
            }

            completed = true;
        }
    }
}

package org.exist.storage;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.UserAider;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for startup triggers.
 */
public class StartupTriggerTest {

    private final static String USER = "testuser1";
    private final static String PASSWORD = "testpass";

    /**
     * Check if startup trigger has access to security manager.
     */
    @Test
    public void createUser() throws DatabaseConfigurationException, EXistException, IOException {
        final Configuration config = new Configuration();
        final List<Configuration.StartupTriggerConfig> startupTriggers =
                (List<Configuration.StartupTriggerConfig>) config.getProperty(BrokerPool.PROPERTY_STARTUP_TRIGGERS);
        startupTriggers.add(new Configuration.StartupTriggerConfig(TestStartupTrigger.class.getName(), null));
        BrokerPool.configure(1, 5, config);
    }

    @After
    public void tearDown() throws IOException, DatabaseConfigurationException, LockException, TriggerException, PermissionDeniedException, EXistException {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
    }

    public static class TestStartupTrigger implements StartupTrigger {

        @Override
        public void execute(final DBBroker sysBroker, final Map<String, List<? extends Object>> params) {
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
        }
    }
}

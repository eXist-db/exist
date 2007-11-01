package org.exist.collections.triggers;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        TriggerConfigTest.class,
        XQueryTriggerTest.class
})
public class AllTriggerTests {
}
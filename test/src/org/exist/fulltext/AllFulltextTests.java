package org.exist.fulltext;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        FTIndexTest.class,
        FtQueryTest.class,
        FTMatchListenerTest.class
})
public class AllFulltextTests {
}

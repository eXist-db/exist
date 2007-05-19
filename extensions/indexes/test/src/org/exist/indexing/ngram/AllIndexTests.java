package org.exist.indexing.ngram;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    CustomIndexTest.class,
    MatchListenerTest.class
})
public class AllIndexTests {
}

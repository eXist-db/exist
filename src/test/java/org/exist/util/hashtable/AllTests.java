package org.exist.util.hashtable;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    NamePoolTest.class,
	HashtableTest.class,
	SequencedLongHashMapTest.class,
	Object2IntHashMapTest.class,
	Int2ObjectHashMapTest.class,
	Object2LongHashMapTest.class,
	Object2LongIdentityHashMapTest.class,
	ObjectHashSetTest.class,
	Long2ObjectHashMapTest.class,
	Object2ObjectHashMapTest.class
})

public class AllTests {
}

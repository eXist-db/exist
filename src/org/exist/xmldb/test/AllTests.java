package org.exist.xmldb.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("Test suite for org.exist.xmldb.test");
		//$JUnit-BEGIN$
		suite.addTest(new TestSuite(CreateCollectionsTest.class));
		suite.addTest(new TestSuite(ResourceTest.class));
//		suite.addTest(new TestSuite(ResourceSetTest.class));
		suite.addTest(new TestSuite(TestEXistXMLSerialize.class));
		//$JUnit-END$
		return suite;
	}
}

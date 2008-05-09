package org.exist.xmldb;

import junit.framework.Test;
import junit.framework.TestSuite;

public class XmldbLocalTests {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    
	public static Test suite() {
		TestSuite suite = new TestSuite("Test suite for org.exist.xmldb");
		//$JUnit-BEGIN$
		suite.addTest(new TestSuite(CreateCollectionsTest.class));
		suite.addTest(new TestSuite(ResourceTest.class));
        suite.addTest(new TestSuite(BinaryResourceUpdateTest.class));
//		suite.addTest(new TestSuite(ResourceSetTest.class));
		suite.addTest(new TestSuite(TestEXistXMLSerialize.class));
		suite.addTest(new TestSuite(CopyMoveTest.class));
        suite.addTest(new TestSuite(ContentAsDOMTest.class));
        suite.addTestSuite(XmldbURITest.class);
        suite.addTestSuite(CollectionConfigurationTest.class);
        suite.addTestSuite(CollectionTest.class);
        suite.addTest(new TestSuite(MultiDBTest.class));
        //$JUnit-END$
		return suite;
	}
}

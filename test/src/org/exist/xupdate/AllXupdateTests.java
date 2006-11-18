package org.exist.xupdate;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * @author berlinge-to
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class AllXupdateTests {

	public static Test suite() {

        XUpdateTest test = new XUpdateTest();
		TestSuite suite = new TestSuite("Test suite for org.exist.xupdate");
   
		//$JUnit-BEGIN$
        
        suite.addTest(new XUpdateTestCases("append", test));
        suite.addTest(new XUpdateTestCases("insertafter", test));
        suite.addTest(new XUpdateTestCases("insertbefore", test));
        suite.addTest(new XUpdateTestCases("remove", test));
        suite.addTest(new XUpdateTestCases("update", test));
        suite.addTest(new XUpdateTestCases("appendAttribute", test));
        suite.addTest(new XUpdateTestCases("appendChild", test));
        suite.addTest(new XUpdateTestCases("insertafter_big", test));
        suite.addTest(new XUpdateTestCases("conditional", test));
        suite.addTest(new XUpdateTestCases("variables", test));
        suite.addTest(new XUpdateTestCases("replace", test));
        suite.addTest(new XUpdateTestCases("whitespace", test));
        suite.addTest(new XUpdateTestCases("namespaces", test));
        
        // bugtest ; fails
//        suite.addTest(new XUpdateTestCases("rename_root_element", test));
        
        // butest ; fails
//        suite.addTest(new XUpdateTestCases("rename_including_namespace", test));
        
//        suite.addTestSuite(RemoveAppendTest.class);
        
        /*
         * create new TestCase
         * -------------------
         * add the following line:
         *
         * suite.addTest(new XUpdateTests(<TestName>, exist));
         * 
         * Param: TestName is the filename of the XUpdateStatement xml file (without '.xml').
         * 
         */
        
		//$JUnit-END$
        
		return suite;
	}

	public static void main(String[] args) {
		TestRunner.run(suite());
	}
}

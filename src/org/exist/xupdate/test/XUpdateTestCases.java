package org.exist.xupdate.test;

import junit.framework.TestCase;

/**
 * @author berlinge-to
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class XUpdateTestCases extends TestCase {

    private XUpdateTest test=null;
    
    public XUpdateTestCases(String name, XUpdateTest test) {
        super(name);
        this.test = test;
    }

    // TestCases Start
    public void append() throws Exception { test.doTest("append", "address.xml"); };
    public void insertafter() throws Exception { test.doTest("insertafter", "address.xml"); };
    public void insertbefore() throws Exception { test.doTest("insertbefore", "address.xml"); };
    public void remove() throws Exception { test.doTest("remove", "address.xml"); };    
    public void update() throws Exception { test.doTest("update", "address.xml"); };
    
    public void insertafter_big() throws Exception { test.doTest("insertafter_big", "address_big.xml"); };
    // <add a new TestCase Method here>     
    
    // TestCases End
    
}

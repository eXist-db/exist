package org.exist.xupdate;

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
    public void append() throws Exception { test.doTest("append", "address.xml"); }
    public void insertafter() throws Exception { test.doTest("insertafter", "address.xml"); }
    public void insertbefore() throws Exception { test.doTest("insertbefore", "address.xml"); }
    public void remove() throws Exception { test.doTest("remove", "address.xml"); }
    public void update() throws Exception { test.doTest("update", "address.xml"); }
    public void appendAttribute() throws Exception { test.doTest("append_attribute", "address.xml"); }
    public void appendChild() throws Exception { test.doTest("append_child", "address.xml"); }
    public void insertafter_big() throws Exception { test.doTest("insertafter_big", "address_big.xml"); }
    public void conditional() throws Exception { test.doTest("conditional", "address.xml"); }
    public void variables() throws Exception { test.doTest("variables", "address.xml"); }
    public void replace() throws Exception { test.doTest("replace", "address.xml"); }
    public void whitespace() throws Exception { test.doTest("whitespace", "address.xml"); }
    public void namespaces() throws Exception { test.doTest("namespaces", "namespaces.xml"); }
    // <add a new TestCase Method here>     

    // Added by Geoff Shuetrim (geoff@galexy.net) on 15 July 2006 to highlight that root element renaming 
    // does not currently succeed, resulting instead in a null pointer exception because the 
    // renaming relies upon obtaining the parent element of the element being renamed and this is null
    // for the root element.
    public void rename_root_element() throws Exception { test.doTest("rename_root_element", "address.xml"); }

    // Added by Geoff Shuetrim (geoff@galexy.net) on 15 July 2006 to highlight that renaming of an
    // element fails when the renaming also involves a change of namespace.
    public void rename_including_namespace() throws Exception { test.doTest("rename_including_namespace", "namespaces.xml"); }
    
    // TestCases End
    
}

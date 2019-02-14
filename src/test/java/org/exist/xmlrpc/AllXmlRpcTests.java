package org.exist.xmlrpc;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    XmlRpcTest.class,
    QuerySessionTest.class
})
public class AllXmlRpcTests {
}

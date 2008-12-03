package org.exist.storage.util;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Dec 2, 2008
 * Time: 11:35:39 AM
 * To change this template use File | Settings | File Templates.
 */
public class TestUtilModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/test";

    public final static String PREFIX = "test";

    private final static FunctionDef functions[] = {
        new FunctionDef(PauseFunction.signature, PauseFunction.class)
    };

    public TestUtilModule() {
        super(functions);
    }

    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getDescription() {
        return "Utility modules used by the test suite.";
    }
}

package org.exist.xqdoc.xquery;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

public class XQDocModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/xqdoc";

    public static final String PREFIX = "xqdm";

    public final static String RELEASED_IN_VERSION = "eXist-1.4.1";

    public static final FunctionDef[] functions = {
        new FunctionDef(Scan.signatures[0], Scan.class),
        new FunctionDef(Scan.signatures[1], Scan.class)
    };

    public XQDocModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, true);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "XQDoc integration module.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}

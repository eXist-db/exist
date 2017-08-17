package org.exist.xquery.modules.persistentlogin;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 * The functions of this module are to be used from within an XQuery which takes care of setting cookies etc.
 *
 * @author Wolfgang Meier
 */
public class PersistentLoginModule extends AbstractInternalModule {

    public final static String NAMESPACE = "http://exist-db.org/xquery/persistentlogin";

    public final static String PREFIX = "plogin";

    private final static String DESCRIPTION = "Persistent login module. Provides functions for implementing a 'remember me' login feature. " +
        "Uses one-time tokens which are valid for a single request only.";

    private final static String RELEASED_IN_VERSION = "eXist-2.0";

    private final static FunctionDef[] functions = {
        new FunctionDef(PersistentLoginFunctions.signatures[0], PersistentLoginFunctions.class),
        new FunctionDef(PersistentLoginFunctions.signatures[1], PersistentLoginFunctions.class),
        new FunctionDef(PersistentLoginFunctions.signatures[2], PersistentLoginFunctions.class)
    };

    public PersistentLoginModule(Map<String, List<?>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
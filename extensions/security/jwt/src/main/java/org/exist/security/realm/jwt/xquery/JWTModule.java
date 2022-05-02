package org.exist.security.realm.jwt.xquery;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

public class JWTModule extends AbstractInternalModule {

    public final static String NAMESPACE = "http://exist-db.org/security/jwt/xquery";

    public final static String PREFIX = "jwt";

    private final static String DESCRIPTION = "JSON Web Token authorization module.";

    private final static String RELEASED_IN_VERSION = "eXist-5.3.0";

    private final static FunctionDef[] functions = functionDefs(
            functionDefs(JWTFunctions.class, JWTFunctions.signatures[0])
    );

    public JWTModule(Map<String, List<?>> parameters) {
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
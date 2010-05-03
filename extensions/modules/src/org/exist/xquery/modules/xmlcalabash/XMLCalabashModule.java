package org.exist.xquery.modules.xmlcalabash;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.modules.example.EchoFunction;

public class XMLCalabashModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://xmlcalabash.com";
    public final static String PREFIX = "xmlcalabash";
    public final static String INCLUSION_DATE = "2010-27-04";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";

    private final static FunctionDef[] functions = {
            new FunctionDef(ProcessFunction.signature, ProcessFunction.class)
        };

    public XMLCalabashModule() {
        super(functions);
    }

    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getDescription() {
        return "A module for performing XML Calabash XProc processing";
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
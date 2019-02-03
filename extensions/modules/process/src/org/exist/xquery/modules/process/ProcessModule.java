package org.exist.xquery.modules.process;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

public class ProcessModule extends AbstractInternalModule {

    public final static String NAMESPACE = "http://exist-db.org/xquery/process";
    public final static String PREFIX = "process";
    public final static String RELEASE = "2.0";

    public final static FunctionDef[] functions = {
        new FunctionDef(Execute.signature, Execute.class)
    };

    public ProcessModule(Map<String, List<?>> parameters) {
        super(functions, parameters, true);
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
        return "A module for executing external processes.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE;
    }
}

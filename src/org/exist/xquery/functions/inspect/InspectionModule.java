package org.exist.xquery.functions.inspect;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

public class InspectionModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/inspection";

    public final static String PREFIX = "inspect";

    public final static String RELEASE = "2.0";

    public final static FunctionDef[] functions = {
        new FunctionDef(InspectFunction.SIGNATURE, InspectFunction.class),
        new FunctionDef(InspectModule.signatures[0], InspectModule.class),
        new FunctionDef(InspectModule.signatures[1], InspectModule.class),
        new FunctionDef(ModuleFunctions.signatures[0], ModuleFunctions.class),
        new FunctionDef(ModuleFunctions.signatures[1], ModuleFunctions.class),
        new FunctionDef(ModuleFunctions.signatures[2], ModuleFunctions.class)
    };

    public InspectionModule(Map<String, List<? extends Object>> parameters) {
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
        return "Functions for inspecting XQuery modules and functions";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE;
    }
}

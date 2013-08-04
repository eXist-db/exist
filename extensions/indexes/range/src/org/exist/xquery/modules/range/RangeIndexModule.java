package org.exist.xquery.modules.range;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

public class RangeIndexModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/range";
    public final static String PREFIX = "range";
    public final static String RELEASED_IN_VERSION = "eXist-2.2";

    public final static FunctionDef[] functions = {
        new FunctionDef(Lookup.signatures[0], Lookup.class),
        new FunctionDef(Lookup.signatures[1], Lookup.class),
        new FunctionDef(Lookup.signatures[2], Lookup.class),
        new FunctionDef(Lookup.signatures[3], Lookup.class),
        new FunctionDef(Lookup.signatures[4], Lookup.class),
        new FunctionDef(FieldLookup.signatures[0], FieldLookup.class),
        new FunctionDef(Optimize.signature, Optimize.class)
    };

    public RangeIndexModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, false);
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
        return "Functions to access the range index.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}

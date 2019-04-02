package org.exist.xquery.functions.map;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 * Implements the XQuery extension for maps as proposed by Michael Kay:
 *
 * http://dev.saxonica.com/blog/mike/2012/01/#000188
 */
public class MapModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/map";
    public static final String PREFIX = "map";

    private static final FunctionDef[] functions = {
            new FunctionDef(MapFunction.FNS_MERGE, MapFunction.class),
            new FunctionDef(MapFunction.FNS_SIZE, MapFunction.class),
            new FunctionDef(MapFunction.FNS_KEYS, MapFunction.class),
            new FunctionDef(MapFunction.FNS_CONTAINS, MapFunction.class),
            new FunctionDef(MapFunction.FNS_GET, MapFunction.class),
            new FunctionDef(MapFunction.FNS_PUT, MapFunction.class),
            new FunctionDef(MapFunction.FNS_ENTRY, MapFunction.class),
            new FunctionDef(MapFunction.FNS_REMOVE, MapFunction.class),
            new FunctionDef(MapFunction.FNS_FOR_EACH, MapFunction.class)
    };

    public MapModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, false);
    }

    public String getNamespaceURI() {
        return "http://www.w3.org/2005/xpath-functions/map";
    }

    public String getDefaultPrefix() {
        return "map";
    }

    public String getDescription() {
        return "Functions that operate on maps";
    }

    public String getReleaseVersion() {
        return "eXist-2.0.x";
    }
}

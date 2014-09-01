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
            new FunctionDef(MapFunction.signatures[0], MapFunction.class),
            new FunctionDef(MapFunction.signatures[1], MapFunction.class),
            new FunctionDef(MapFunction.signatures[2], MapFunction.class),
            new FunctionDef(MapFunction.signatures[3], MapFunction.class),
            new FunctionDef(MapFunction.signatures[4], MapFunction.class),
            new FunctionDef(MapFunction.signatures[5], MapFunction.class),
            new FunctionDef(MapFunction.signatures[6], MapFunction.class),
            new FunctionDef(MapFunction.signatures[7], MapFunction.class),
            new FunctionDef(MapFunction.signatures[8], MapFunction.class)
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

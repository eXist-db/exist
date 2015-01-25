package org.exist.xquery.functions.array;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 * Module implementing functions that operate on arrays.
 */
public class ArrayModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/array";
    public static final String PREFIX = "array";

    private static final FunctionDef[] functions = {
            new FunctionDef(ArrayFunction.signatures[0], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[1], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[2], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[3], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[4], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[5], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[6], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[7], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[8], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[9], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[10], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[11], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[12], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[13], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[14], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[15], ArrayFunction.class),
            new FunctionDef(ArrayFunction.signatures[16], ArrayFunction.class)
    };

    public ArrayModule(Map<String, List<? extends Object>> parameters) {
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
        return "Functions that operate on arrays";
    }

    @Override
    public String getReleaseVersion() {
        return "2.2.1";
    }
}
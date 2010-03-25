package org.exist.xquery.modules.sort;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

public class SortModule extends AbstractInternalModule {

    public final static String        NAMESPACE_URI       = "http://exist-db.org/xquery/sort";

    public final static String        PREFIX              = "sort";
    public final static String        INCLUSION_DATE      = "2010-03-22";
    public final static String        RELEASED_IN_VERSION = "&lt; eXist-1.5";

    public final static FunctionDef[] functions           = {
        new FunctionDef(CreateOrderIndex.signatures[0], CreateOrderIndex.class),
        new FunctionDef(CreateOrderIndex.signatures[1], CreateOrderIndex.class),
        new FunctionDef(GetIndex.signature, GetIndex.class),
        new FunctionDef(RemoveIndex.signatures[0], RemoveIndex.class),
        new FunctionDef(RemoveIndex.signatures[1], RemoveIndex.class)
    };

    public SortModule() {
        super(functions, true);
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
        return "Creates and manages pre-ordered indexes for use with an 'order by' expression.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}

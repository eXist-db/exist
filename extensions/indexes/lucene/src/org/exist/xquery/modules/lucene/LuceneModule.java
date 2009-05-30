package org.exist.xquery.modules.lucene;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

public class LuceneModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/lucene";

    public static final String PREFIX = "ft";

    public static final FunctionDef[] functions = {
        new FunctionDef(Query.signature, Query.class),
        new FunctionDef(Score.signature, Score.class),
        new FunctionDef(IndexTerms.signatures[0], IndexTerms.class),
        new FunctionDef(IndexTerms.signatures[1], IndexTerms.class)
    };

    public LuceneModule() {
        super(functions, false);
    }

    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getDescription() {
        return "Extension functions for Lucene search.";
    }
}


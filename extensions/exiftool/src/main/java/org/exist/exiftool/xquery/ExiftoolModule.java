package org.exist.exiftool.xquery;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:dulip.withanage@gmail.com">Dulip Withanage</a>
 * @version 1.0
 */
public class ExiftoolModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/exiftool";
    public final static String PREFIX = "exiftool";
    public final static String INCLUSION_DATE = "2011-04-11";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";

    public final static FunctionDef[] functions = {
        new FunctionDef(MetadataFunctions.getMetadata, MetadataFunctions.class)
    };

    public ExiftoolModule(Map<String, List<? extends Object>> parameters) throws XPathException {
        super(functions, parameters, true);
    }

    @Override
    public String getNamespaceURI() {
        return (NAMESPACE_URI);
    }

    @Override
    public String getDefaultPrefix() {
        return (PREFIX);
    }

    @Override
    public String getDescription() {
        return ("Module for reading and writing embedded metadata for binary files");
    }

    @Override
    public String getReleaseVersion() {
        return (RELEASED_IN_VERSION);
    }

    protected String getExiftoolPath() {
        return (String)getParameter("exiftool-path").get(0);
    }

    protected String getPerlPath() {
        return (String)getParameter("perl-path").get(0);
    }

  
}
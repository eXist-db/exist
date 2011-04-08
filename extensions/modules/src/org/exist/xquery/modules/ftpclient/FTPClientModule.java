
package org.exist.xquery.modules.ftpclient;

import java.util.Map;
import java.util.List;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;



/**
 *
 * @author WStarcev
 */
public class FTPClientModule extends AbstractInternalModule {
    public final static String         NAMESPACE_URI                  = "http://exist-db.org/xquery/ftpclient";

    public final static String         PREFIX                         = "ftpclient";
    public final static String         INCLUSION_DATE                 = "2011-03-24";
    public final static String         RELEASED_IN_VERSION            = "eXist-1.2";

    public final static String         HTTP_MODULE_PERSISTENT_STATE = "_eXist_ftpclient_module_persistent_state";


    private final static FunctionDef[] functions                      = {
        new FunctionDef( GetDirListFunction.signature, GetDirListFunction.class ),
        new FunctionDef( SendFileFunction.signature, SendFileFunction.class ),
        new FunctionDef( GetFileFunction.signature, GetFileFunction.class )
    };


    public FTPClientModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }


    public String getNamespaceURI()
    {
        return( NAMESPACE_URI );
    }


    public String getDefaultPrefix()
    {
        return( PREFIX );
    }


    public String getDescription()
    {
        return( "A module for performing FTP requests as a client" );
    }


    public String getReleaseVersion()
    {
        return( RELEASED_IN_VERSION );
    }

}

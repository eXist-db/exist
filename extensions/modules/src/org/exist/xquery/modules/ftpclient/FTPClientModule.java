
package org.exist.xquery.modules.ftpclient;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Map.Entry;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.modules.ModuleUtils.ContextMapEntryModifier;

/**
 *
 * @author WStarcev
 * @author Adam Retter <adam@existsolutions.com>
 */
public class FTPClientModule extends AbstractInternalModule {
    public final static String         NAMESPACE_URI                  = "http://exist-db.org/xquery/ftpclient";

    public final static String         PREFIX                         = "ftpclient";
    public final static String         INCLUSION_DATE                 = "2011-03-24";
    public final static String         RELEASED_IN_VERSION            = "eXist-1.2";

    public final static String CONNECTIONS_CONTEXTVAR = "_eXist_ftp_connections";
    
    private static final Logger log = LogManager.getLogger(FTPClientModule.class);

    private final static FunctionDef[] functions = {
        new FunctionDef(GetConnectionFunction.signatures[0], GetConnectionFunction.class),
        new FunctionDef(GetDirListFunction.signature, GetDirListFunction.class),
        new FunctionDef(SendFileFunction.signature, SendFileFunction.class),
        new FunctionDef(GetFileFunction.signature, GetFileFunction.class)
    };

    public FTPClientModule(Map<String, List<?>> parameters) {
        super(functions, parameters);
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
        return "A module for performing FTP requests as a client";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
    
    /**
     * Stores a Connection in the Context of an XQuery.
     *
     * @param   context  The Context of the XQuery to store the Connection in
     * @param   ftp      The connection to store
     *
     * @return  A unique ID representing the connection
     */
    public static synchronized long storeConnection(XQueryContext context, FTPClient ftp) {
        return ModuleUtils.storeObjectInContextMap(context, FTPClientModule.CONNECTIONS_CONTEXTVAR, ftp);
    }
    
    /**
     * Retrieves a previously stored Connection from the Context of an XQuery.
     *
     * @param   context        The Context of the XQuery containing the Connection
     * @param   connectionUID  The UID of the Connection to retrieve from the Context of the XQuery
     *
     * @return  DOCUMENT ME!
     */
    public static FTPClient retrieveConnection(XQueryContext context, long connectionUID) {
        return ModuleUtils.retrieveObjectFromContextMap(context, FTPClientModule.CONNECTIONS_CONTEXTVAR, connectionUID);
    }
    
    /**
     * Resets the Module Context and closes any FTP connections for the XQueryContext.
     *
     * @param  xqueryContext  The XQueryContext
     */
    @Override
    public void reset(XQueryContext xqueryContext, boolean keepGlobals) {
        // reset the module context
        super.reset(xqueryContext, keepGlobals);

        // close any open Connections
        closeAllConnections(xqueryContext);
    }
    
    /**
     * Closes all the open DB Connections for the specified XQueryContext.
     *
     * @param  xqueryContext  The context to close JDBC Connections for
     */
    private static void closeAllConnections(XQueryContext xqueryContext) {
        
        ModuleUtils.modifyContextMap(xqueryContext, FTPClientModule.CONNECTIONS_CONTEXTVAR, new ContextMapEntryModifier<FTPClient>(){
            
            @Override 
            public void modify(Map<Long, FTPClient> map) {
                super.modify(map);
                
                //empty the map
                map.clear();
            }
            
            @Override
            public void modify(Entry<Long, FTPClient> entry) {
            
                final FTPClient con = entry.getValue();
                
                try {
                    // close the Connection
                    con.logout();
                } catch(IOException ioe) {
                    log.error(ioe.getMessage(), ioe);
                } finally {
                    if(con.isConnected()) {
                        try {
                            con.disconnect();
                        } catch(IOException ioe) {
                            log.error(ioe.getMessage(), ioe);
                        }
                    }
                }
            }
        });

        // update the context
        //ModuleUtils.storeContextMap(xqueryContext, FTPClientModule.CONNECTIONS_CONTEXTVAR, connections);
    }
}
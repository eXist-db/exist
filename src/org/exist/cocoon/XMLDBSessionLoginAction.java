
package org.exist.cocoon;

import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.acting.ServiceableAction;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.thread.ThreadSafe;

import java.util.HashMap;
import java.util.Map;

import org.xmldb.api.*;
import org.xmldb.api.base.*;

/**
 *  Cocoon action to authenticate a user against the database.
 * 
 * If authentication succeeds, user and password will be stored into
 * the current session.
 *
 * @author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class XMLDBSessionLoginAction extends ServiceableAction
     implements ThreadSafe {

    public Map act( Redirector redirector, SourceResolver resolver,
                    Map objectModel, String source, Parameters param ) throws Exception {
        Request request = ObjectModelHelper.getRequest( objectModel );
        if ( request == null ) {
            getLogger().error( "XMLDBSessionLoginAction: no request object!" );
            return null;
        }
        Map map = new HashMap();
        String user = null;
        String passwd = null;

        // check for valid session
        Session session = request.getSession( false );
        if ( session == null ||
            ( !request.isRequestedSessionIdValid() ) )
            // create a new session
            session = request.getSession( true );

        // check user account and store it to the session
        if ( session.getAttribute( "user" ) == null ) {
            // try to read parameters from sitemap
            user = param.getParameter( "user", null );
            passwd = param.getParameter( "password", null );
            // else try to read parameters from request
            if ( user == null ) {
                user = request.getParameter( "user" );
                passwd = request.getParameter( "password" );
            }
            if ( user == null ) {
                getLogger().error( "XMLDBSessionLoginAction: no parameters!" );
                return null;
            }
            if ( source == null ) {
                getLogger().error( "XMLDBSessionLoginAction: no source specified!" );
                return null;
            }
            getLogger().info( "trying to login user " + user );

            // try to access collection specified in source
            try {
                Collection collection =
                    DatabaseManager.getCollection( source, user, passwd );
            } catch ( XMLDBException e ) {
                getLogger().error( "login denied: " + e.getMessage() );
                return null;
            }

            // store user info to session
            session.setAttribute( "user", user );
            session.setAttribute( "password", passwd );
        }
        else {
            // retrieve user info from session
            user = (String) session.getAttribute( "user" );
            passwd = (String) session.getAttribute( "password" );
        }
        // return data to the sitemap
        map.put( "user", user );
        map.put( "password", passwd );
        return map;
    }
}


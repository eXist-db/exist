
package org.exist.xmlrpc;
import java.lang.reflect.*;

import java.util.Vector;
import org.apache.log4j.Category;

import org.apache.xmlrpc.AuthenticatedXmlRpcHandler;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcException;
import org.exist.EXistException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    25. August 2002
 */
public class AuthenticatedHandler implements AuthenticatedXmlRpcHandler {
    private static Category LOG =
        Category.getInstance( AuthenticatedXmlRpcHandler.class.getName() );

    private RpcAPI handler;
    private BrokerPool pool = null;


    /**
     *  Constructor for the AuthenticatedHandler object
     *
     *@param  conf                 Description of the Parameter
     *@exception  XmlRpcException  Description of the Exception
     */
    public AuthenticatedHandler( Configuration conf ) throws XmlRpcException {
        try {
            handler = new RpcServer( conf );
            pool = BrokerPool.getInstance();
        } catch ( EXistException e ) {
            throw new XmlRpcException( 0, e.toString() );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  method         Description of the Parameter
     *@param  v              Description of the Parameter
     *@param  user           Description of the Parameter
     *@param  password       Description of the Parameter
     *@return                Description of the Return Value
     *@exception  Exception  Description of the Exception
     */
    public Object execute( String method, Vector v,
                           String user, String password ) throws Exception {
        // assume guest user if no user is specified
        // set a password for admin to permit this
        if ( user == null ) {
            user = "guest";
            password = "guest";
        }
        // check user
        User u = pool.getSecurityManager().getUser( user );
        if ( u == null )
            throw new XmlRpcException( 0, "User " + user + " unknown" );
        if ( !u.validate( password ) ) {
            if ( XmlRpc.debug )
                LOG.debug( "login denied for user " + user );
            throw new XmlRpcException( 0, "Invalid password for user " + user );
        }
        if ( XmlRpc.debug ) {
            LOG.debug( "user " + user + " logged in" );
            LOG.debug( "calling " + method );
            for ( int i = 0; i < v.size(); i++ )
                LOG.debug( "argument " + i + ": " + v.elementAt( i ) );
        }
        return execute( u, method, v );
    }


    private Object execute( User user, String methodName,
                            Vector params ) throws Exception {
        Class[] argClasses = null;
        Object[] argValues = null;
        if ( params != null ) {
            argClasses = new Class[params.size() + 1];
            argValues = new Object[params.size() + 1];
            argValues[0] = user;
            argClasses[0] = User.class;
            for ( int i = 1; i < params.size() + 1; i++ ) {
                argValues[i] = params.elementAt( i - 1 );
                if ( argValues[i] instanceof Integer )
                    argClasses[i] = Integer.TYPE;

                else if ( argValues[i] instanceof Double )
                    argClasses[i] = Double.TYPE;

                else if ( argValues[i] instanceof Boolean )
                    argClasses[i] = Boolean.TYPE;

                else
                    argClasses[i] = argValues[i].getClass();

            }
        }
        else {
            argClasses = new Class[1];
            argValues = new Class[1];
            argValues[0] = user;
            argClasses[0] = User.class;
        }
        Method method = null;
        try {
            method = RpcAPI.class.getMethod( methodName, argClasses );
        } catch ( NoSuchMethodException nsm_e ) {
            throw nsm_e;
        } catch ( SecurityException s_e ) {
            throw s_e;
        }

        // Our policy is to make all public methods callable except
        // the ones defined in java.lang.Object.
        if ( method.getDeclaringClass() == Object.class )
            throw new XmlRpcException( 0, "Invoker can't call methods " +
                "defined in java.lang.Object" );

        // invoke
        Object returnValue = null;
        try {
            returnValue = method.invoke( handler, argValues );
        } catch ( IllegalAccessException iacc_e ) {
            throw iacc_e;
        } catch ( IllegalArgumentException iarg_e ) {
            throw iarg_e;
        } catch ( InvocationTargetException it_e ) {
            // check whether the thrown exception is XmlRpcException
            Throwable t = it_e.getTargetException();
            if ( t instanceof XmlRpcException )
                throw (XmlRpcException) t;
            // It is some other exception
            throw new Exception( t.toString() );
        }
        return returnValue;
    }
}


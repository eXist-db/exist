/**
 *  AdminSoapBindingImpl.java This file was auto-generated from WSDL by the
 *  Apache Axis Wsdl2java emitter.
 */

package org.exist.soap;
import java.io.UnsupportedEncodingException;

import java.rmi.RemoteException;

import org.apache.log4j.Category;
import org.exist.Parser;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

import org.w3c.dom.Document;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    August 2, 2002
 */
public class AdminSoapBindingImpl implements org.exist.soap.Admin {

    private static Category LOG =
        Category.getInstance( Admin.class.getName() );

    private BrokerPool pool;


    /**  Constructor for the AdminSoapBindingImpl object */
    public AdminSoapBindingImpl() {
        try {
            pool = BrokerPool.getInstance();
        } catch ( Exception e ) {
            throw new RuntimeException( "failed to initialize broker pool" );
        }
    }

	public String connect(String user, String password) throws java.rmi.RemoteException {
		User u = pool.getSecurityManager().getUser(user);
		if (u == null)
			throw new RemoteException("user " + user + " does not exist");
		if (!u.validate(password))
			throw new RemoteException("the supplied password is invalid");
		LOG.debug("user " + user + " connected");
		return SessionManager.getInstance().createSession(u);
	}

	public void disconnect(String id) throws RemoteException {
		SessionManager manager = SessionManager.getInstance();
		Session session = manager.getSession(id);
		if (session != null) {
			LOG.debug("disconnecting session " + id);
			manager.disconnect(id);
		}
	}
	
    public boolean createCollection( String sessionId, String collection )
         throws RemoteException {
			Session session = SessionManager.getInstance().getSession(sessionId); 	
        DBBroker broker = null;
        try {
            broker = pool.get();
            LOG.debug( "creating collection " + collection );
            org.exist.collections.Collection coll = 
            	broker.getOrCreateCollection( session.getUser(), collection );
            if ( coll == null ) {
                LOG.debug( "failed to create collection" );
                return false;
            }
            broker.saveCollection( coll );
            broker.flush();
            broker.sync();
            return true;
        } catch ( Exception e ) {
            LOG.debug( e );
            throw new RemoteException( e.getMessage() );
        } finally {
            pool.release( broker );
        }
    }

    public boolean removeCollection( String sessionId, String collection )
         throws RemoteException {
		Session session = SessionManager.getInstance().getSession(sessionId);
        DBBroker broker = null;
        try {
            broker = pool.get();
            if ( broker.getCollection( collection ) == null )
                return false;
            return broker.removeCollection( session.getUser(), collection );
        } catch ( Exception e ) {
            LOG.debug( e.getMessage(), e );
            throw new RemoteException( e.getMessage(), e );
        } finally {
            pool.release( broker );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  path                 Description of the Parameter
     *@return                      Description of the Return Value
     *@exception  RemoteException  Description of the Exception
     */
    public boolean removeDocument( String sessionId, String path ) throws RemoteException {
    	Session session = SessionManager.getInstance().getSession(sessionId);
        DBBroker broker = null;
        try {
            broker = pool.get();
            if ( broker.getDocument( session.getUser(), path ) == null )
                return false;
            broker.removeDocument( session.getUser(), path );
            return true;
        } catch ( Exception e ) {
            LOG.debug( e.getMessage(), e );
            throw new RemoteException( e.getMessage(), e );
        } finally {
            pool.release( broker );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  data                 Description of the Parameter
     *@param  encoding             Description of the Parameter
     *@param  path                 Description of the Parameter
     *@param  replace              Description of the Parameter
     *@exception  RemoteException  Description of the Exception
     */
    public void store( String sessionId, byte[] data, java.lang.String encoding,
                       java.lang.String path, boolean replace )
         throws RemoteException {
        Session session = SessionManager.getInstance().getSession(sessionId);
        DBBroker broker = null;
        try {
            broker = pool.get();
            if ( broker.getDocument( session.getUser(), path ) != null ) {
                if ( !replace )
                    throw new RemoteException( "document " +
                        path + " exists and parameter replace is set to false." );
            }
            String xml;
            try {
                xml = new String( data, encoding );
            } catch ( UnsupportedEncodingException e ) {
                throw new RemoteException( e.getMessage() );
            }
            long startTime = System.currentTimeMillis();
            Parser parser = new Parser( broker, session.getUser(), true );
            Document doc = parser.parse( xml, path );
            LOG.debug( "flushing data files" );
            broker.flush();
            LOG.debug( "parsing " + path + " took " +
                ( System.currentTimeMillis() - startTime ) + "ms." );
        } catch ( Exception e ) {
            LOG.debug( e );
            throw new RemoteException( e.getMessage(), e );
        } finally {
            pool.release( broker );
        }
    }
    
	private Session getSession(String id) throws java.rmi.RemoteException {
			Session session = SessionManager.getInstance().getSession(id);
			if (session == null)
				throw new java.rmi.RemoteException("Session is invalid or timed out");
			return session;
		}
}


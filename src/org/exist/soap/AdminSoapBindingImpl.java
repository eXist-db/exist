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


    /**
     *  Description of the Method
     *
     *@param  collection           Description of the Parameter
     *@return                      Description of the Return Value
     *@exception  RemoteException  Description of the Exception
     */
    public boolean createCollection( String collection )
         throws RemoteException {
        DBBroker broker = null;
        try {
            broker = pool.get();
            LOG.debug( "creating collection " + collection );
            org.exist.dom.Collection coll = broker.getOrCreateCollection( collection );
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


    /**
     *  Description of the Method
     *
     *@param  collection           Description of the Parameter
     *@return                      Description of the Return Value
     *@exception  RemoteException  Description of the Exception
     */
    public boolean removeCollection( String collection )
         throws RemoteException {
        DBBroker broker = null;
        try {
            broker = pool.get();
            if ( broker.getCollection( collection ) == null )
                return false;
            return broker.removeCollection( collection );
        } catch ( Exception e ) {
            LOG.debug( e );
            throw new RemoteException( e.getMessage() );
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
    public boolean removeDocument( String path ) throws RemoteException {
        DBBroker broker = null;
        try {
            broker = pool.get();
            if ( broker.getDocument( path ) == null )
                return false;
            broker.removeDocument( path );
            return true;
        } catch ( Exception e ) {
            LOG.debug( e );
            throw new RemoteException( e.getMessage() );
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
    public void store( byte[] data, java.lang.String encoding,
                       java.lang.String path, boolean replace )
         throws RemoteException {
        DBBroker broker = null;
        try {
            broker = pool.get();
            if ( broker.getDocument( path ) != null ) {
                if ( replace ) {
                    LOG.debug( "removing document " + path );
                    broker.removeDocument( path );
                }
                else
                    throw new RemoteException( "document " +
                        path + " already exists in the database." );
            }
            broker.flush();
            String xml;
            try {
                xml = new String( data, encoding );
            } catch ( UnsupportedEncodingException e ) {
                throw new RemoteException( e.getMessage() );
            }
            long startTime = System.currentTimeMillis();
            Parser parser = new Parser( broker, new User( "admin", null, "dba" ), true );
            Document doc = parser.parse( xml, path );
            LOG.debug( "flushing data files" );
            broker.flush();
            LOG.debug( "sync" );
            broker.sync();
            LOG.debug( "parsing " + path + " took " +
                ( System.currentTimeMillis() - startTime ) + "ms." );
        } catch ( Exception e ) {
            LOG.debug( e );
            throw new RemoteException( e.getMessage() );
        } finally {
            pool.release( broker );
        }
    }
}


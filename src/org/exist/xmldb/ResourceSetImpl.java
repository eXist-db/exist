
package org.exist.xmldb;

import java.util.Vector;
import org.apache.xmlrpc.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

/**
 *  Description of the Class
 *
 *@author     wolf
 *@created    24. April 2002
 */
public class ResourceSetImpl implements ResourceSet {

    protected CollectionImpl collection;
    protected String encoding = "UTF-8";
    protected int indentXML = 1;
    protected Vector resources;

    protected XmlRpcClient rpcClient;


    /**
     *  Constructor for the ResourceSetImpl object
     *
     *@param  col  Description of the Parameter
     */
    public ResourceSetImpl( CollectionImpl col ) {
        this.collection = col;
        resources = new Vector();
    }


    /**
     *  Constructor for the ResourceSetImpl object
     *
     *@param  col        Description of the Parameter
     *@param  resources  Description of the Parameter
     *@param  indentXML  Description of the Parameter
     *@param  encoding   Description of the Parameter
     */
    public ResourceSetImpl( CollectionImpl col, Vector resources,
                            int indentXML, String encoding ) {
        this.resources = resources;
        this.collection = col;
        this.indentXML = indentXML;
        this.encoding = encoding;
    }


    /**
     *  Adds a feature to the Resource attribute of the ResourceSetImpl object
     *
     *@param  resource  The feature to be added to the Resource attribute
     */
    public void addResource( Resource resource ) {
        resources.addElement( resource );
    }


    /**
     *  Description of the Method
     *
     *@exception  XMLDBException  Description of the Exception
     */
    public void clear() throws XMLDBException {
        resources.clear();
    }


    /**
     *  Gets the iterator attribute of the ResourceSetImpl object
     *
     *@return                     The iterator value
     *@exception  XMLDBException  Description of the Exception
     */
    public ResourceIterator getIterator() throws XMLDBException {
        return new NewResourceIterator();
    }


    /**
     *  Gets the iterator attribute of the ResourceSetImpl object
     *
     *@param  start               Description of the Parameter
     *@return                     The iterator value
     *@exception  XMLDBException  Description of the Exception
     */
    public ResourceIterator getIterator( long start ) throws XMLDBException {
        return new NewResourceIterator( start );
    }


    /**
     *  Gets the membersAsResource attribute of the ResourceSetImpl object
     *
     *@return                     The membersAsResource value
     *@exception  XMLDBException  Description of the Exception
     */
    public Resource getMembersAsResource() throws XMLDBException {
        throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
    }


    /**
     *  Gets the resource attribute of the ResourceSetImpl object
     *
     *@param  pos                 Description of the Parameter
     *@return                     The resource value
     *@exception  XMLDBException  Description of the Exception
     */
    public Resource getResource( long pos ) throws XMLDBException {
        if ( pos >= resources.size() )
            return null;
        // node or value?
        if ( resources.elementAt( (int) pos ) instanceof Vector ) {
            // node
            Vector v = (Vector) resources.elementAt( (int) pos );
            String doc = (String) v.elementAt( 0 );
            String s_id = (String) v.elementAt( 1 );

            String docColl = doc.substring( 0, doc.lastIndexOf( '/' ) );
            CollectionImpl temp = (CollectionImpl)
                DatabaseImpl.readCollection( docColl, collection.getClient(), null );
            XMLResource res =
                new XMLResourceImpl( temp, doc, s_id, indentXML, encoding );
            return res;
        }
        else if ( resources.elementAt( (int) pos ) instanceof Resource )
            return (Resource) resources.elementAt( (int) pos );
        else {
            // value
            XMLResource res = new XMLResourceImpl( collection, Long.toString( pos ),
                null, indentXML, encoding );
            res.setContent( resources.elementAt( (int) pos ) );
            return res;
        }
    }


    /**
     *  Gets the size attribute of the ResourceSetImpl object
     *
     *@return                     The size value
     *@exception  XMLDBException  Description of the Exception
     */
    public long getSize() throws XMLDBException {
        return (long) resources.size();
    }


    /**
     *  Description of the Method
     *
     *@param  pos                 Description of the Parameter
     *@exception  XMLDBException  Description of the Exception
     */
    public void removeResource( long pos ) throws XMLDBException {
        resources.removeElementAt( (int) pos );
    }


    /**
     *  Description of the Class
     *
     *@author     wolf
     *@created    24. April 2002
     */
    class NewResourceIterator implements ResourceIterator {

        long pos = 0;


        /**  Constructor for the NewResourceIterator object */
        public NewResourceIterator() { }


        /**
         *  Constructor for the NewResourceIterator object
         *
         *@param  start  Description of the Parameter
         */
        public NewResourceIterator( long start ) {
            pos = start;
        }


        /**
         *  Description of the Method
         *
         *@return                     Description of the Return Value
         *@exception  XMLDBException  Description of the Exception
         */
        public boolean hasMoreResources() throws XMLDBException {
            return pos < resources.size();
        }


        /**
         *  Description of the Method
         *
         *@return                     Description of the Return Value
         *@exception  XMLDBException  Description of the Exception
         */
        public Resource nextResource() throws XMLDBException {
            return getResource( pos++ );
        }
    }
}


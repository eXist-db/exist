
package org.exist.xmldb;

import java.util.Iterator;
import java.util.Vector;

import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SortedNodeSet;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.xpath.Value;
import org.exist.xpath.ValueSet;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

public class LocalResourceSet implements ResourceSet {

    protected BrokerPool brokerPool;
    protected LocalCollection collection;
    protected String encoding = "UTF-8";
    protected boolean indentXML = false;
    protected Vector resources = new Vector();
    protected boolean saxDocumentEvents = true;
    protected boolean createContainerElements = false;
    protected int highlightMatches = 0;
    private User user;


    public LocalResourceSet( User user, BrokerPool pool, LocalCollection col ) {
        this.collection = col;
        this.brokerPool = pool;
        this.user = user;
    }

    public LocalResourceSet( User user, BrokerPool pool, LocalCollection col, 
                             Value val,
                             boolean indentXML, String encoding, 
                             boolean saxDocumentEvents, 
                             boolean createContainerElements,
                             int highlightMatches,
                             String sortExpr )
         throws XMLDBException {
        this.user = user;
        this.brokerPool = pool;
        this.encoding = encoding;
        this.collection = col;
        this.indentXML = indentXML;
        this.saxDocumentEvents = saxDocumentEvents;
        this.createContainerElements = createContainerElements;
        this.highlightMatches = highlightMatches;
        switch ( val.getType() ) {
            case Value.isNodeList:
                NodeSet resultSet = (NodeSet)val.getNodeList();
                if(sortExpr != null) {
                	SortedNodeSet sorted = 
                		new SortedNodeSet(brokerPool, user, sortExpr);
                	sorted.addAll(resultSet);
                	resultSet = sorted;
                }
                NodeProxy p;
                for ( Iterator i = resultSet.iterator();
                    i.hasNext();  ) {
                    p = (NodeProxy) i.next();
                    if ( p == null )
                        continue;
                    resources.add( p );
                }
                break;
            default:
	        	ValueSet valueSet = val.getValueSet();
	        	Value v;
	        	for (int i = 0; i < valueSet.getLength(); i++) {
	        		v = valueSet.get(i);
	        		resources.add(v.getStringValue());
	        	}
	        	break;
        }
    }

    public void addResource( Resource resource ) throws XMLDBException {
        resources.add( resource );
    }

    public void clear() throws XMLDBException {
        resources.clear();
    }

    public ResourceIterator getIterator() throws XMLDBException {
        return new NewResourceIterator();
    }

    public ResourceIterator getIterator( long start ) throws XMLDBException {
        return new NewResourceIterator( start );
    }

    public Resource getMembersAsResource() throws XMLDBException {
        throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
    }

    public Resource getResource( long pos ) throws XMLDBException {
        if ( pos < 0 || pos >= resources.size() )
            return null;
        Object r = resources.get( (int) pos );
    	LocalXMLResource res = null;
        if(r instanceof NodeProxy) {
        	NodeProxy p = (NodeProxy)r;
        	// the resource might belong to a different collection
			// than the one by which this resource set has been
			// generated: adjust if necessary.
        	LocalCollection coll = collection;
			if ( coll == null || p.doc.getCollection() == null ||
				coll.collection.getId() != p.doc.getCollection().getId() ) {
				coll =
					new LocalCollection( user, brokerPool, null,
						p.doc.getCollection() );
			}
			res = new LocalXMLResource( user, brokerPool, coll, p, indentXML );
			res.setSAXDocEvents( saxDocumentEvents );
			res.setEncoding( encoding );
			res.setCreateContainerElements( createContainerElements );
			res.setMatchTagging(highlightMatches);
        } else if(r instanceof String) {
        	res = new LocalXMLResource( user, brokerPool, collection, null, -1);
        	res.setContent( r );
        } else if(r instanceof Resource)
        	return (Resource)r;
        return res;
    }


    /**
     *  Gets the size attribute of the LocalResourceSet object
     *
     *@return                     The size value
     *@exception  XMLDBException  Description of the Exception
     */
    public long getSize() throws XMLDBException {
        return resources.size();
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
     *@created    3. Juni 2002
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
            return pos < getSize();
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


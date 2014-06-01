/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.validation.internal;

import org.apache.log4j.Logger;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.source.ClassLoaderSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Helper class for accessing grammars.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class DatabaseResources {
    
    public final static String QUERY_LOCATION = "org/exist/validation/internal/query/";
    
    public final static String FIND_XSD = QUERY_LOCATION + "find_schema_by_targetNamespace.xq";
    
    public final static String FIND_CATALOGS_WITH_DTD = QUERY_LOCATION + "find_catalogs_with_dtd.xq";
    
    public final static String PUBLICID = "publicId";
    
    public final static String TARGETNAMESPACE = "targetNamespace";
    
    public final static String CATALOG    = "catalog";
    
    public final static String COLLECTION = "collection";
    
    /** Local reference to database  */
    private BrokerPool brokerPool = null;
    
    /** Local logger */
    private final static Logger logger = Logger.getLogger(DatabaseResources.class);
    
    
    /**
     *  Convert sequence into list of strings.
     *
     * @param   sequence  Result of query.
     * @return  List containing String objects.
     */
    public List<String> getAllResults(Sequence sequence){
        List<String> result = new ArrayList<String>();
        
        try {
            final SequenceIterator i = sequence.iterate();         
            while(i.hasNext()){
                final String path =  i.nextItem().getStringValue();
                result.add(path);
            }
            
        } catch (final XPathException ex) {
            logger.error("xQuery issue.", ex);
            result=null;
        }
        
        return result;
    }
    
    /**
     *  Get first entry of sequence as String.
     *
     * @param   sequence  Result of query.
     * @return  String containing representation of 1st entry of sequence.
     */
    public String getFirstResult(Sequence sequence){
        String result = null;
        
        try {
            final SequenceIterator i = sequence.iterate();
            if(i.hasNext()){
                result= i.nextItem().getStringValue();
                
                logger.debug("Single query result: '"+result+"'.");
                
            } else {
                logger.debug("No query result.");
            }
            
        } catch (final XPathException ex) {
            logger.error("xQuery issue ", ex);
        }
        
        return result;
    }
    
    
    public Sequence executeQuery(String queryPath, Map<String,String> params, Subject user){
        
        final String namespace = params.get(TARGETNAMESPACE);
        final String publicId = params.get(PUBLICID);
        final String catalogPath = params.get(CATALOG);
        final String collection = params.get(COLLECTION);
        
        if(logger.isDebugEnabled()) {
            logger.debug("collection=" + collection + " namespace=" + namespace
                + " publicId="+publicId + " catalogPath="+catalogPath);
        }
        
        DBBroker broker = null;
        Sequence result= null;
        try {
            broker = brokerPool.get(user);
            
            CompiledXQuery compiled =null;
            final XQuery xquery = broker.getXQueryService();
            final XQueryContext context = xquery.newContext(AccessContext.INTERNAL_PREFIX_LOOKUP);
            
            if(collection!=null){
                context.declareVariable(COLLECTION, collection);
            }
            
            if(namespace!=null){
                context.declareVariable(TARGETNAMESPACE, namespace);
            }
            
            if(publicId!=null){
                context.declareVariable(PUBLICID, publicId);
            }
            
            if(catalogPath!=null){
                context.declareVariable(CATALOG, catalogPath);
            }
            
            compiled = xquery.compile(context, new ClassLoaderSource(queryPath) );
            
            result = xquery.execute(compiled, null);
            
        } catch (final Exception ex) {
            logger.error("Problem executing xquery", ex);
            result= null;
            
        } finally{
            if(brokerPool!=null){
                brokerPool.release(broker);
            }
        }
        return result;
    }
    
    
    /**
     * Creates a new instance of DatabaseResources.
     * 
     * 
     * 
     * @param pool  Instance shared broker pool.
     */
    public DatabaseResources(BrokerPool pool) {
        
        logger.info("Initializing DatabaseResources");
        this.brokerPool = pool;
        
    }
    
    public String findXSD(String collection, String targetNamespace, Subject user){
        
        if(logger.isDebugEnabled()) {
            logger.debug("Find schema with namespace '"+targetNamespace+"' in '"+collection+"'.");
        }
        
        final Map<String,String> params = new HashMap<String,String>();
        params.put(COLLECTION, collection);
        params.put(TARGETNAMESPACE, targetNamespace);
        
        final Sequence result = executeQuery(FIND_XSD, params, user );
        
        return getFirstResult(result);
    }
    
    public String findCatalogWithDTD(String collection, String publicId, Subject user){
        
        if(logger.isDebugEnabled()) {
            logger.debug("Find DTD with public '"+publicId+"' in '"+collection+"'.");
        }
        
        final Map<String,String> params = new HashMap<String,String>();
        params.put(COLLECTION, collection);
        params.put(PUBLICID, publicId);
        
        final Sequence result = executeQuery(FIND_CATALOGS_WITH_DTD, params, user );
        
        return getFirstResult(result);
    }
    
}

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  $Id$
 */

package org.exist.validation.resolver;

import java.io.IOException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import org.apache.log4j.Logger;
import org.apache.xml.resolver.Catalog;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *   Shared CatalogResolver to be used by the eXist database and the eXist
 * Cocoon web interface. The configuration must be initialized once, typically
 * performed by the database (Configuration class). Consequently the Cocoon
 * interface can reuse the resolver.
 *
 * This resolver guarantees that only one apache.org catalog resolver is being
 * initialized. At this moment the class is just a thin wrapper. To be changed
 * in the near future: Grammar and catalog files can all be stored in the
 * database itself.
 *
 * @see <a href="http://xml.apache.org/commons/components/resolver/"
 *                                                      >Apache.org resolver</a>
 * @see <a href="http://xml.apache.org/commons/components/resolver/resolver-article.html"
 *                                             >XML Entity and URI Resolvers</a>
 *
 * @author Dannes Wessels
 */
public class eXistCatalogResolver implements org.xml.sax.EntityResolver,
        javax.xml.transform.URIResolver {
    
    private final static Logger logger = Logger.getLogger(eXistCatalogResolver.class);
    private static CatalogResolver catalogResolver = null;
    
    
    /** Constructor. */
    public eXistCatalogResolver() {
        logger.debug("Initializing eXistCatalogResolver");
        if(catalogResolver==null){
            catalogResolver = new CatalogResolver();
        }
    }
    
    /**
     * Constructor.
     *
     * @param privateCatalog  TRUE for private catalog, FALSE if not.
     */
    public eXistCatalogResolver(boolean privateCatalog) {
        logger.debug("Initializing eXistCatalogResolver, privateCatalog="+privateCatalog);
        if(catalogResolver==null){
            catalogResolver = new CatalogResolver();
        }
    }
    
    /**
     * Constructor.
     *
     * @param manager Specific catalogmanager to use.
     */
    public eXistCatalogResolver(CatalogManager manager) {
        logger.debug("Initializing eXistCatalogResolver, with manager.");
        if(catalogResolver==null){
            catalogResolver = new CatalogResolver(manager);
        }
    }
    
    
    /**
     *  Return the underlying catalog
     * @return catalog object.
     */
    public Catalog getCatalog() {
        logger.debug("Getting catalog from eXistCatalogResolver.");
        
        Catalog catalog = catalogResolver.getCatalog();
        
        if(catalog==null){
            logger.error("Catalog could not be retrieved.");
        }
            
        return catalog;
    }
    
    /**
     *  Resolve grammar specified by publicId and/or systemId.
     *
     * @see  org.xml.sax.EntityResolver#resolveEntity  resolveEntity
     *
     * @param  publicId  The public identifier of the external entity being
     *                   referenced, or null if none was supplied.
     * @param  systemId  The system identifier of the external entity being
     *                   referenced.
     * @throws SAXException     Any SAX exception, possibly wrapping another
     *                          exception.
     * @throws IOException      A Java-specific IO exception, possibly the
     *                          result of creating a new InputStream or Reader
     *                          for the InputSource.
     * @return An InputSource object describing the new input source, or null
     *         to request that the parser open a regular URI connection to the
     *         system identifier.
     */
    public InputSource resolveEntity(String publicId, String systemId)
    throws SAXException, IOException {
        
        logger.debug("resolveEntity( publicId='" +publicId + "', systemId='"+systemId+"').");
        InputSource inputsource =catalogResolver.resolveEntity(publicId, systemId);
        
        if(inputsource==null){
            // According to the spec 'null' must be returned. However, this
            // value is for the Parser the hint to use the systemId that is 
            // supplied to the resolver. Unfortunately this value does not make
            // any sence; cocoon let is point to it cache:
            // tools/jetty/work/Jetty__8080__exist/cocoon-files/cache-dir/*.dtd
            // With this value is seems this resolver is not functional at all.
            // We'll return null at this moment.
            logger.debug("Entity could not be resolved");
            return null;
            
        } else {
            
            logger.debug("resolved publicId='"+inputsource.getPublicId()
                        + "' systemId='"+inputsource.getSystemId()+"'.");
        }
        
        if(inputsource.getByteStream()==null){
            logger.debug("No data stream returned from resolved Entitity.");
            //inputsource.setSystemId(null);
        }
        
        return inputsource;
    }
    
    
    /**
     *  An object that implements this interface that can be called by the
     * processor to turn a URI used in document(), xsl:import, or xsl:include
     * into a Source object.
     *
     * @see javax.xml.transform.URIResolver#resolve resolve
     *
     * @param  href  An href attribute, which may be relative or absolute.
     * @param  base  The base URI in effect when the href attribute was
     *               encountered.
     * @throws TransformerException  if an error occurs when trying to resolve
     *                               the URI.
     * @return A Source object, or null if the href cannot be resolved, and the
     *         processor should try to resolve the URI itself.
     */
    public Source resolve(String href, String base) throws TransformerException {
        logger.debug("resolve( href='" +href + "', base='"+base+"').");
        Source source= catalogResolver.resolve(href, base);
        
        if(source==null){
            logger.debug("href could not be resolved");
        } else {
            logger.debug("systemId="+source.getSystemId());
        }
        
        return source;
    }
}

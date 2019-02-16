/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
package org.exist.validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.util.XMLGrammarPoolImpl;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.exist.Namespaces;

/**
 *  Wrapper around the Xerces XMLGrammarPoolImpl, so debugging of
 * actions can be monitored. Javadoc copied from xml.apache.org.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 *
 * @see org.apache.xerces.xni.grammars.XMLGrammarPool
 * @see org.apache.xerces.util.XMLGrammarPoolImpl
 * @see org.apache.xerces.xni.grammars.Grammar
 * @see org.apache.xerces.xni.grammars.XMLGrammarDescription
 */
public class GrammarPool implements XMLGrammarPool {
    
    private final static Logger logger = LogManager.getLogger(GrammarPool.class);
    
    private final XMLGrammarPool pool;
    
    /**
     * Constructs a grammar pool with a default number of buckets. 
     */
    public GrammarPool() {
        if (logger.isInfoEnabled())
            {logger.info("Initializing GrammarPool.");}
        pool = new XMLGrammarPoolImpl();
    }
    
    /**  
     * Constructs a grammar pool with a default number of buckets using pool.
     * 
     * @param pool The supplied grammar pool is reused.
     */
    public GrammarPool(XMLGrammarPool pool) {
        if (logger.isInfoEnabled())
            {logger.info("Initializing GrammarPool using supplied pool.");}
        this.pool=pool;
    }
    
    /**
     *   Retrieve the initial known set of grammars. this method is called
     * by a validator before the validation starts. the application can provide 
     * an initial set of grammars available to the current validation attempt.
     *
     * @see org.apache.xerces.xni.grammars.XMLGrammarPool#retrieveInitialGrammarSet(String)
     * 
     * @param   type  The type of the grammar, from the 
     *          org.apache.xerces.xni.grammars.Grammar interface.
     * @return  The set of grammars the validator may put in its "bucket"
     */
    public Grammar[] retrieveInitialGrammarSet(String type) {
        if (logger.isDebugEnabled())
            {logger.debug("Retrieve initial grammarset ("+type+").");}
        
        final Grammar[] grammars = pool.retrieveInitialGrammarSet(type);
        if (logger.isDebugEnabled())
            {logger.debug("Found "+grammars.length+" grammars.");}
        return grammars;
    }
    
    /**
     *  Return the final set of grammars that the validator ended up with.
     *
     * @see org.apache.xerces.xni.grammars.XMLGrammarPool#cacheGrammars(String,Grammar[])
     * 
     * @param type      The type of the grammars being returned
     * @param grammar   an array containing the set of grammars being 
     *                  returned; order is not significant.
     */
    public void cacheGrammars(String type, Grammar[] grammar) {
        if (logger.isDebugEnabled())
            {logger.debug("Cache "+grammar.length+" grammars ("+type + ").");}
        pool.cacheGrammars(type, grammar);
    }
        
    /**
     *  Allows the XMLGrammarPool to store grammars when its 
     * cacheGrammars(String, Grammar[]) method is called. This is the default 
     * state of the object.
     *
     * @see org.apache.xerces.xni.grammars.XMLGrammarPool#unlockPool
     */
    public void unlockPool() {
        if (logger.isDebugEnabled())
            {logger.debug("Unlock grammarpool.");}
        pool.unlockPool();
    }
    
    /**
     *   This method requests that the application retrieve a grammar 
     * corresponding to the given GrammarIdentifier from its cache. If it 
     * cannot do so it must return null; the parser will then call the 
     * EntityResolver. An application must not call its EntityResolver itself 
     * from this method; this may result in infinite recursions.
     *
     * @see org.apache.xerces.xni.grammars.XMLGrammarPool#retrieveGrammar(XMLGrammarDescription)
     *
     * @param xgd    The description of the Grammar being requested.
     * @return       the Grammar corresponding to this description or null 
     *               if no such Grammar is known.
     */
    public Grammar retrieveGrammar(XMLGrammarDescription xgd) {

        if (xgd == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("XMLGrammarDescription is null");
            }
            return null;
        }

        if (xgd.getNamespace() != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Retrieve grammar for namespace '" + xgd.getNamespace() + "'.");
            }
        }

        if (xgd.getPublicId() != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Retrieve grammar for publicId '" + xgd.getPublicId() + "'.");
            }
        }

        return pool.retrieveGrammar(xgd);
    }
    
    /**
     *  Causes the XMLGrammarPool not to store any grammars when the 
     * cacheGrammars(String, Grammar[[]) method is called.
     *
     * @see org.apache.xerces.xni.grammars.XMLGrammarPool#lockPool
     */
    public void lockPool() {
        if (logger.isDebugEnabled())
            {logger.debug("Lock grammarpool.");}
        pool.lockPool();
    }
    
    /**
     *  Removes all grammars from the pool.
     *
     * @see org.apache.xerces.xni.grammars.XMLGrammarPool#clear
     */
    public void clear() {
        if (logger.isDebugEnabled())
            {logger.debug("Clear grammarpool.");}
        pool.clear();
    }
    
    /**
     *  Removes all DTD grammars from the pool. Workaround for Xerces bug.
     *
     * @see org.apache.xerces.xni.grammars.XMLGrammarPool#clear
     */
    public void clearDTDs() {
        //if (logger.isDebugEnabled())
        //    logger.debug("Removing DTD's from grammarpool.");

        final Grammar dtds[] = retrieveInitialGrammarSet(Namespaces.DTD_NS);
        if (dtds.length > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Removing " + dtds.length + " DTDs.");
            }
            final Grammar schemas[] = retrieveInitialGrammarSet(Namespaces.SCHEMA_NS);
            clear();
            cacheGrammars(Namespaces.SCHEMA_NS, schemas);
            
        } else {
            //if (logger.isDebugEnabled())
            //    logger.debug("No DTDs to be removed.");
        }
    }
    
    
}

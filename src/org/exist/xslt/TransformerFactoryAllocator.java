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
 *  $Id: TransformerFactoryAllocator.java 0000 2006-08-10 22:39:00 +0000 (Thu, 10 Aug 2006) deliriumsky $
 */
package org.exist.xslt;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;

/**
 * Allows the TransformerFactory that is used for XSLT to be
 * chosen through configuration settings in conf.xml
 *
 * Within eXist this class should be used instead of
 * directly calling SAXTransformerFactory.newInstance() directly
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */

public class TransformerFactoryAllocator
{
    
    private final static Logger LOG = Logger.getLogger(TransformerFactoryAllocator.class);
    
    public static final String CONFIGURATION_ELEMENT_NAME = "transformer";
    public final static String TRANSFORMER_CLASS_ATTRIBUTE = "class";
    public final static String PROPERTY_TRANSFORMER_CLASS = "transformer.class";
    
    //private constructor
    private TransformerFactoryAllocator() {
    }
    
    /**
     * Get the TransformerFactory defined in conf.xml
     * If the class can't be found or the given class doesn't implement
     * the required interface, the default factory is returned.
     *
     * @param broker A database broker, used for reading the conf.xml configuration
     *
     * @return  A SAXTransformerFactory, for which newInstance() can then be called
     *
     *
     * Typical usage:
     *
     * Instead of SAXTransformerFactory.newInstance() use
     * TransformerFactoryAllocator.getTransformerFactory(broker).newInstance()
     */
    public static SAXTransformerFactory getTransformerFactory(DBBroker broker) {
        SAXTransformerFactory factory;
        
        //get the transformer class name from conf.xml
        String transformerFactoryClassName =
                (String)broker.getConfiguration().getProperty(PROPERTY_TRANSFORMER_CLASS);
        
//        LOG.debug("transformerFactoryClassName=" + transformerFactoryClassName);
//        LOG.debug("javax.xml.transform.TransformerFactory="
//                + System.getProperty("javax.xml.transform.TransformerFactory"));
        
        
        //was a TransformerFactory class specified
        if(transformerFactoryClassName == null) {
            //no, use the system default
            factory = (SAXTransformerFactory)TransformerFactory.newInstance();
        } else {
            //try and load the specified TransformerFactory class
            try {
                factory = (SAXTransformerFactory)
                Class.forName(transformerFactoryClassName).newInstance();
            } catch(ClassNotFoundException cnfe) {
                LOG.debug("Cannot find the requested TrAX factory '"
                        + transformerFactoryClassName
                        + "'. Using default TrAX Transformer Factory instead." );
                
                //fallback to system default
                factory = (SAXTransformerFactory)TransformerFactory.newInstance();
            } catch(ClassCastException cce) {
                LOG.debug( "The indicated class '" + transformerFactoryClassName
                        + "' is not a TrAX Transformer Factory. "
                        +"Using default TrAX Transformer Factory instead." );
                
                //fallback to system default
                factory = (SAXTransformerFactory)TransformerFactory.newInstance();
            } catch(Exception e) {
                LOG.debug( "Error found loading the requested TrAX Transformer Factory '"
                        + transformerFactoryClassName
                        + "'. Using default TrAX Transformer Factory instead." );
                
                //fallback to system default
                factory = (SAXTransformerFactory)TransformerFactory.newInstance();
            }
        }
        
        return factory;
    }
    
}

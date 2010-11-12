/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xslt;

import java.util.Map;

import org.exist.interpreter.ContextAtExist;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xslt.functions.XSLModule;

import javax.xml.transform.Transformer;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLContext extends XQueryContext implements ContextAtExist {

	private XSLStylesheet xslStylesheet;

    public XSLContext(DBBroker broker) {
    	super(broker, AccessContext.XSLT);

    	init();
    }

	private void init() {
    	setWatchDog(new XQueryWatchDog(this));
    	
        loadDefaultNS();
        

        Configuration config = broker.getConfiguration();
        // Get map of built-in modules
        Map<String, Class<Module>> builtInModules = (Map)config.getProperty( PROPERTY_BUILT_IN_MODULES );

        if( builtInModules != null ) {

            // Iterate on all map entries
            for( Map.Entry<String, Class<Module>> entry : builtInModules.entrySet() ) {

                // Get URI and class
                String        namespaceURI = entry.getKey();
                Class<Module> moduleClass  = entry.getValue();
                
                // first check if the module has already been loaded in the parent context
                Module        module       = getModule( namespaceURI );

                if( module == null ) {
                    // Module does not exist yet, instantiate
                    instantiateModule( namespaceURI, moduleClass );

                } else if( ( getPrefixForURI( module.getNamespaceURI() ) == null )
                           && ( module.getDefaultPrefix().length() > 0 ) ) {

                    // make sure the namespaces of default modules are known,
                    // even if they were imported in a parent context
                    try {
                        declareNamespace( module.getDefaultPrefix(), module.getNamespaceURI() );
                        
                    } catch( XPathException e ) {
                        LOG.warn( "Internal error while loading default modules: " + e.getMessage(), e );
                    }
                }
            }
        }
        //UNDERSTAND: what to do?
    	try {
			importModule(XSLModule.NAMESPACE_URI,
					XSLModule.PREFIX,
					"java:org.exist.xslt.functions.XSLModule");
		} catch (XPathException e) {
			e.printStackTrace();
		}
	}
	
	protected void loadDefaultNS() {
		super.loadDefaultNS();
		
	}
	
	public void setXSLStylesheet(XSLStylesheet xsl) {
		this.xslStylesheet = xsl;
	}

	public XSLStylesheet getXSLStylesheet() {
		return xslStylesheet;
	}

	public Transformer getTransformer() {
		return xslStylesheet.getTransformer();
	}
}

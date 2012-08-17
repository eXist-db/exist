/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.messaging.configuration;

import javax.naming.Context;
import org.exist.xquery.XPathException;



/**
 *
 * @author wessels
 */
public class JmsMessagingConfiguration extends MessagingConfiguration {

    public String getConnectionFactory() {
        String baseName = getRootName();
        return getRawConfigurationItem(baseName + "." + "ConnectionFactory");
    }

    public String getDestination() {
        String baseName = getRootName();
        return getRawConfigurationItem(baseName + "." + "Destination");
    }
    
    public String getInitalContextProperty(String key){
        String baseName = getRootName();
        return getRawConfigurationItem(baseName + ".InitialContext." + key);
    }
    
    @Override
    public void validateContent() throws XPathException {
        
        String initialContextFactory = getInitalContextProperty(Context.INITIAL_CONTEXT_FACTORY);
        if(initialContextFactory==null){
            throw new XPathException("Missing configuration item '" + Context.INITIAL_CONTEXT_FACTORY+"'");
        }
        
        String providerURL = getInitalContextProperty(Context.PROVIDER_URL);
        if(providerURL==null){
            throw new XPathException("Missing configuration item '" + Context.PROVIDER_URL +"'");
        }
        
        String connectionFactory = getConnectionFactory();
        if(connectionFactory==null){
            throw new XPathException("Missing configuration item 'ConnectionFactory'");
        }
        
        String destination = getDestination();
        if(destination==null){
            throw new XPathException("Missing configuration item 'Destination'");
        }
        
    }
}

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
package org.exist.replication.shared;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.naming.Context;
import org.apache.log4j.Logger;

/**
 *
 * @author Dannes Wessels
 */
public abstract class ClientParameters {

    protected final static Logger LOG = Logger.getLogger(ClientParameters.class);
    
    public static final String CONNECTION_FACTORY = "connectionfactory";
    public static final String TOPIC = "topic";
    public static final String CLIENT_ID = "client-id";
   
    protected String connectionFactory;
    protected String clientId;
    protected String topic;
    
    protected String initialContextFactory;
    protected String providerUrl;

    protected Properties props = new Properties();

    /**
     *  Get all JMS  settings from supplied parameters.
     * 
     * @param params Multi value parameters
     */
    public void setMultiValueParameters(Map<String, List<?>> params) {

        for(final String key : params.keySet()) {

            List<?> values = params.get(key);
            if (values != null && !values.isEmpty()) {

                // Only get first value
                Object value = values.get(0);
                if (value instanceof String) {
                    props.setProperty(key, (String) value);
                }
            }
        }

    }

    /**
     *  Get all JMS settings from supplied parameters.
     * 
     * @param params Single valued parameters.
     * 
     * @return  Values as properties.
     */
    public void setSingleValueParameters(final Map<String, List<? extends Object>> params) {

        for(final String key : params.keySet()) {
            final String value = getConfigurationValue(params, key);
            if(value != null){
                props.setProperty(key, value);
            }
        }

    }

    /**
     * Retrieve configuration value when available as String.
     *
     * @param params Map containing all parameter values
     * @param name Name of configuration item
     * @return Value of item, or NULL if not existent or existent and not a
     * String object
     */
    private static String getConfigurationValue(final Map<String, List<? extends Object>> params, String name) {

        String retVal = null;

        final List<? extends Object> value = params.get(name);
        if(value != null) {
            if(value.size() > 0) {
                retVal = value.get(0).toString();
            }
        }

        return retVal;
    }

    /**
     * Fill properties object with default values for
     * java.naming.factory.initial and java.naming.provider.url if not provided.
     * Defaults are set to match the Apache ActiveMQ message broker on
     * localhost.
     *
     */
    public void fillActiveMQbrokerDefaults() {

        if (props.getProperty(Context.INITIAL_CONTEXT_FACTORY) == null) {
            String defaultValue = "org.apache.activemq.jndi.ActiveMQInitialContextFactory";
            props.setProperty(Context.INITIAL_CONTEXT_FACTORY, defaultValue);
            LOG.info("No value set for '" + Context.INITIAL_CONTEXT_FACTORY + "', "
                    + "using default value '" + defaultValue + "' "
                    + "which is suitable for activeMQ");
        }

        if (props.getProperty(Context.PROVIDER_URL) == null) {
            String defaultValue = "tcp://localhost:61616";
            props.setProperty(Context.PROVIDER_URL, defaultValue);
            LOG.info("No value set for '" + Context.PROVIDER_URL
                    + "', using default value '" + defaultValue + "' "
                    + "which is suitable for activeMQ");
        }

    }
    
    /**
     *  Retrieve initial context properties, e.g. {@link Context.INITIAL_CONTEXT_FACTORY}
     * and {@link Context.PROVIDER_URL}
     */
    public Properties getInitialContextProps(){
        Properties contextProps = new Properties();
        
        // Copy all properties that start with "java."
        for(String key : props.stringPropertyNames()){
            if(key.startsWith("java.")){
                contextProps.setProperty(key, props.getProperty(key));
            }
        }
                
        return contextProps;
    }
    
    abstract public void processParameters() throws TransportException, ClientParameterException;
    
    abstract public String getReport();
    
    public String getConnectionFactory() {
        return connectionFactory;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTopic() {
        return topic;
    }

    public Properties getProps() {
        return props;
    }
    
    public String getInitialContextFactory() {
        return initialContextFactory;
    }

    public String getProviderUrl() {
        return providerUrl;
    }
    
}

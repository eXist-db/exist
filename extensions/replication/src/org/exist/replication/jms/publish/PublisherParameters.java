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
package org.exist.replication.jms.publish;

import javax.naming.Context;
import org.exist.replication.shared.ClientParameters;
import org.exist.replication.shared.TransportException;

/**
 * Publisher specific properties.
 * 
 * @author Dannes Wessels dannes@exist-db.org
 */
public class PublisherParameters extends ClientParameters {

    public static final String TIME_TO_LIVE = "time-to-live";
    public static final String PRIORITY = "priority";
    
    private Long timeToLive;
    private Integer priority;

    public Long getTimeToLive() {
        return timeToLive;
    }

    public Integer getPriority() {
        return priority;
    }

    @Override
    public void processParameters() throws TransportException {
        
        // java.naming.factory.initial
        String value = props.getProperty( Context.INITIAL_CONTEXT_FACTORY );
        initialContextFactory=value;
        
        // java.naming.provider.url
        value = props.getProperty( Context.PROVIDER_URL );
        providerUrl=value;
        
        // Connection factory
        value = props.getProperty(CONNECTION_FACTORY);
        if (value == null || value.equals("")) {
            value = "ConnectionFactory";
            LOG.info("No " + CONNECTION_FACTORY + " set, using default value '" + value + "'");
        }
        connectionFactory = value;
        

        // Setup destination
        value = props.getProperty(TOPIC);
        if (value == null || value.equals("")) {
            value = "dynamicTopics/eXistdb";
            LOG.info("No " + TOPIC + " set (topic), using default value '" + value
                    + "' which is suitable for activeMQ");
        }
        topic = value;

        value = props.getProperty(CLIENT_ID);
        if (value == null || value.equals("")) {
            String errorText = "'" + CLIENT_ID + "' is not set.";
            LOG.error(errorText);
            throw new TransportException(errorText);
        }
        clientId=value;


        // Get time to live value
        value = props.getProperty(TIME_TO_LIVE);
        if (value != null && !value.equals("")) {
            try {
                timeToLive = Long.valueOf(value);
            } catch (NumberFormatException ex) {
                String errorText = "Unable to set TTL; got '" + value + "'. " + ex.getMessage();
                LOG.error(errorText);
                throw new TransportException(errorText);
            }
        }

        // Get priority
        value = props.getProperty(PRIORITY);
        if (value != null && !value.equals("")) {
            try {
                priority = Integer.valueOf(value);
            } catch (NumberFormatException ex) {
                String errorText = "Unable to set priority; got '" + value + "'. " + ex.getMessage();
                LOG.error(errorText);
                throw new TransportException(errorText);
            }
        }
    }

    @Override
    public String getReport() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Publisher configuration: ");
        
        sb.append(Context.INITIAL_CONTEXT_FACTORY).append("='").append(initialContextFactory).append("'");
        sb.append(" ");
        
        sb.append(Context.PROVIDER_URL).append("='").append(providerUrl).append("'");
        sb.append(" ");
        
        sb.append(TOPIC).append("='").append(topic).append("'");
        sb.append(" ");
        
        sb.append(CLIENT_ID).append("='").append(clientId).append("'");
        sb.append(" ");
        
        sb.append(TIME_TO_LIVE).append("='").append(timeToLive).append("'");
        sb.append(" ");
        
        sb.append(PRIORITY).append("='").append(priority).append("'");
        
        return sb.toString();
    }
}

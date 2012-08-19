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
package org.exist.replication.jms.subscribe;

import javax.naming.Context;
import org.exist.replication.shared.ClientParameters;
import org.exist.scheduler.JobException;

/**
 * Subscriber  specific properties.
 * 
 * @author Dannes Wessels dannes@exist-db.org
 */
public class SubscriberParameters extends ClientParameters {

    public static final String SUBSCRIBER_NAME = "subscriber-name";
    public static final String MESSAGE_SELECTOR = "messageselector";
    public static final String DURABLE = "durable";
    public static final String NO_LOCAL = "nolocal";
    
    private String subscriberName;
    private String messageSelector;
    
    private boolean noLocal=Boolean.TRUE;
    private boolean durable=Boolean.TRUE;

    public boolean isDurable() {
        return durable;
    }

    public String getSubscriberName() {
        return subscriberName;
    }

    public String getMessageSelector() {
        return messageSelector;
    }

    public boolean isNoLocal() {
        return noLocal;
    }

    @Override
    public void processParameters() throws JobException {
        
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


        // Destination / topic
        value = props.getProperty(TOPIC);
        if (value == null || value.equals("")) {
            value = "dynamicTopics/eXistdb";
            LOG.info("No " + TOPIC + " set (topic), using default value '" + value
                    + "' which is suitable for activeMQ");
        }
        topic = value;

        // Client ID
        value = props.getProperty(CLIENT_ID);
        if (value == null || value.equals("")) {
            String errorText = "'" + CLIENT_ID + "' is not set.";
            LOG.error(errorText);
            throw new JobException(JobException.JOB_ABORT_THIS, errorText);
        }
        clientId = value;


        // Get subscribername
        value = props.getProperty(SUBSCRIBER_NAME);
        if (value == null || value.equals("")) {
            String errorText = "'" + SUBSCRIBER_NAME + "' is not set.";
            LOG.error(errorText);
            throw new JobException(JobException.JOB_ABORT_THIS, errorText);
        }
        subscriberName=value;

        // Get messageSelector
        value = props.getProperty(MESSAGE_SELECTOR);
        if (value != null) {
            LOG.info("Message selector '" + messageSelector + "'");
            messageSelector = value;
        }

        // Get NoLocal value, default no local copies
        value = props.getProperty(NO_LOCAL);
        if (value != null) {

            if ("FALSE".equalsIgnoreCase(value) || "NO".equalsIgnoreCase(value)) {
                noLocal = false;

            } else if ("TRUE".equalsIgnoreCase(value) || "YES".equalsIgnoreCase(value)) {
                noLocal = true;

            } else {
                String errorText = "'" + NO_LOCAL + "' contains wrong value '" + value + "'";
                LOG.error(errorText);
                throw new JobException(JobException.JOB_ABORT_THIS, errorText);
            }

        }
        
        // Get Durable value, 
        value = props.getProperty(DURABLE);
        if (value != null) {

            if ("FALSE".equalsIgnoreCase(value) || "NO".equalsIgnoreCase(value)) {
                durable = false;

            } else if ("TRUE".equalsIgnoreCase(value) || "YES".equalsIgnoreCase(value)) {
                durable = true;

            } else {
                String errorText = "'" + DURABLE + "' contains wrong value '" + value + "'";
                LOG.error(errorText);
                throw new JobException(JobException.JOB_ABORT_THIS, errorText);
            }

        }
    }

    @Override
    public String getReport() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Subscriber configuration: ");
        
        sb.append(Context.INITIAL_CONTEXT_FACTORY).append("='").append(initialContextFactory).append("'");
        sb.append(" ");
        
        sb.append(Context.PROVIDER_URL).append("='").append(providerUrl).append("'");
        sb.append(" ");
        
        sb.append(TOPIC).append("='").append(topic).append("'");
        sb.append(" ");
        
        sb.append(CLIENT_ID).append("='").append(clientId).append("'");
        sb.append(" ");
        
        sb.append(SUBSCRIBER_NAME).append("='").append(subscriberName).append("'");
        sb.append(" ");
        
        sb.append(MESSAGE_SELECTOR).append("='").append(messageSelector).append("'");
        sb.append(" ");
        
        sb.append(NO_LOCAL).append("='").append(noLocal).append("'");
        sb.append(" ");
        
        sb.append(DURABLE).append("='").append(durable).append("'");
        
        return sb.toString();
    }
}

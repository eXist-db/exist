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

import java.util.Map;
import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.exist.replication.shared.JmsConnectionExceptionListener;
import org.exist.scheduler.JobException;
import org.exist.scheduler.UserJavaJob;
import org.exist.storage.BrokerPool;

/**
 * Startup 'job' to fire-up a message receiver. Typically this job is started by
 * configuration in conf.xml
 *
 * @author Dannes Wessels
 */
public class MessageReceiverJob extends UserJavaJob {

    private final static Logger LOG = Logger.getLogger(MessageReceiverJob.class);
    private final static String JOB_NAME = "MessageReceiverJob";
    private String jobName = JOB_NAME;
    
    /**
     * Helper method to give resources back
     */
    private void closeSilent(Context context, Connection connection, Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException ex) {
                LOG.debug(ex);
            }
        }
        
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException ex) {
                LOG.debug(ex);
            }
        }
        
        if (context != null) {
            try {
                context.close();
            } catch (NamingException ex) {
                LOG.debug(ex);
            }
        }
    }

    @Override
    public void execute(BrokerPool brokerpool, Map<String, ?> params) throws JobException {
        
        // Get from .xconf file, fill defaults when needed
        SubscriberParameters parameters = new SubscriberParameters();
        parameters.setSingleValueParameters(params);
        parameters.processParameters();
        parameters.fillActiveMQbrokerDefaults();
        
        LOG.info("Starting subscription of '" + parameters.getSubscriberName() 
                    + "' to '" + parameters.getTopic() + "'");

        if(LOG.isDebugEnabled()){
            LOG.debug(parameters.getReport());
        }

        // Setup listeners
        JMSMessageListener jmsListener = new JMSMessageListener(brokerpool);
        ExceptionListener exceptionListener = new JmsConnectionExceptionListener();
        
        Context context = null;
        Connection connection = null;
        Session session = null;

        try {
            // Setup context
            Properties contextProps = parameters.getInitialContextProps();
            context = new InitialContext(contextProps);

            // Lookup topic
            Destination destination = (Destination) context.lookup(parameters.getTopic());
            if (!(destination instanceof Topic)) {
                String errorText = "'" + parameters.getTopic() + "' is not a Topic.";
                LOG.error(errorText);
                throw new JobException(JobException.JobExceptionAction.JOB_ABORT_THIS, errorText);
            }

            // Lookup connection factory            
            ConnectionFactory cf = (ConnectionFactory) context.lookup(parameters.getConnectionFactory());

            // Setup connection
            connection = cf.createConnection();
            
            // Register for exceptions
            connection.setExceptionListener(exceptionListener);

            // Set clientId
            connection.setClientID(parameters.getClientId());

            // TODO DW: should this be configurable?
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Set durable messaging, when required
            if (parameters.isDurable()) {
                // Set subscriber
                TopicSubscriber topicSubscriber = session.createDurableSubscriber(
                        (Topic) destination,
                        parameters.getSubscriberName(),
                        parameters.getMessageSelector(),
                        parameters.isNoLocal());

                // Register listeners
                topicSubscriber.setMessageListener(jmsListener);

            } else {
                // Create message consumer
                MessageConsumer messageConsumer = session.createConsumer(
                        destination,
                        parameters.getMessageSelector(),
                        parameters.isNoLocal());


                // Register listeners
                messageConsumer.setMessageListener(jmsListener);
            }

            // Start it all
            connection.start();

            LOG.info("Subscription was successful.");

        } catch (Throwable t) {
            // Close all that has been opened. Always.
            closeSilent(context, connection, session);
            
            LOG.error("Unable to start subscription: " + t.getMessage() + ";  " + parameters.getReport(), t);
            throw new JobException(JobException.JobExceptionAction.JOB_ABORT_THIS, t.getMessage());
        }
    }

    @Override
    public String getName() {
        return jobName;
    }

    @Override
    public void setName(String name) {
        jobName = name;
    }
}

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

import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.Logger;
import org.exist.replication.shared.MessageSender;
import org.exist.replication.shared.TransportException;
import org.exist.replication.shared.eXistMessage;

/**
 * Specific class for sending a eXistMessage via JMS to a broker
 *
 * @author Dannes Wessels
 */
public class JMSMessageSender implements MessageSender {

    private final static Logger LOG = Logger.getLogger(JMSMessageSender.class);
    private PublisherParameters parameters = new PublisherParameters();

    /**
     * Constructor
     *
     * @param parameters Set of (Key,value) parameters for setting JMS routing
     * instructions, like java.naming.* , destination and connection factory.
     */
    JMSMessageSender(Map<String, List<?>> params) {
        parameters.setMultiValueParameters(params);
    }

    /**
     * Helper method to give resources back
     */
    private void closeAll(Context context, Connection connection, Session session) {

        boolean doLog = LOG.isDebugEnabled();

        if (session != null) {
            if (doLog) {
                LOG.debug("Closing session");
            }

            try {
                session.close();
            } catch (JMSException ex) {
                LOG.error(ex);
            }
        }

        if (connection != null) {
            if (doLog) {
                LOG.debug("Closing connection");
            }
            
            try {
                connection.close();
            } catch (JMSException ex) {
                LOG.error(ex);
            }
        }

        if (context != null) {
            if (doLog) {
                LOG.debug("Closing context");
            }
             
            try {
                context.close();
            } catch (NamingException ex) {
                LOG.error(ex);
            }
        }
    }

    /**
     * Send {@link eXistMessage} to message broker.
     *
     * @param em The message that needs to be sent
     * @throws TransportException Thrown when something bad happens.
     */
    public void sendMessage(eXistMessage em) throws TransportException {

        // Get from .xconf file, fill defaults when needed
        parameters.processParameters();

        Properties contextProps = parameters.getInitialContextProps();

        if(LOG.isDebugEnabled()){
            LOG.debug(parameters.getReport());
        }

        Context context = null;
        Connection connection = null;
        Session session = null;

        try {
            // Setup context
            context = new InitialContext(contextProps);

            // Lookup connection factory        
            ConnectionFactory cf = (ConnectionFactory) context.lookup(parameters.getConnectionFactory());

            // Setup connection
            connection = cf.createConnection();

            // Set clientId if present
            String clientId = parameters.getClientId();
            if (clientId != null) {
                connection.setClientID(clientId);
            }

            // TODO DW: should this be configurable?
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Lookup topic
            Destination destination = (Destination) context.lookup(parameters.getTopic());
            if (!(destination instanceof Topic)) {
                String errorText = "'" + parameters.getTopic() + "' is not a Topic.";
                LOG.error(errorText);
                throw new TransportException(errorText);
            }

            // Create message
            MessageProducer producer = session.createProducer(destination);

            // Set time-to-live is set
            Long timeToLive = parameters.getTimeToLive();
            if (timeToLive != null) {
                producer.setTimeToLive(timeToLive);
            }

            // Set priority if set
            Integer priority = parameters.getPriority();
            if (priority != null) {
                producer.setPriority(priority);
            }

            BytesMessage message = session.createBytesMessage();

            // Set payload when available
            byte[] payload = em.getPayload();
            if (payload != null) {
                message.writeBytes(payload); // check empty, collection!
            }

            // Set eXist-db clustering specific details
            message.setStringProperty(eXistMessage.EXIST_RESOURCE_OPERATION, em.getResourceOperation().name());
            message.setStringProperty(eXistMessage.EXIST_RESOURCE_TYPE, em.getResourceType().name());
            message.setStringProperty(eXistMessage.EXIST_SOURCE_PATH, em.getResourcePath());

            if (em.getDestinationPath() != null) {
                message.setStringProperty(eXistMessage.EXIST_DESTINATION_PATH, em.getDestinationPath());
            }


            // Set other details
            Map<String, Object> metaData = em.getMetadata();
            for (String item : metaData.keySet()) {
                Object value = metaData.get(item);

                if (value instanceof String) {
                    message.setStringProperty(item, (String) value);

                } else if (value instanceof Integer) {
                    message.setIntProperty(item, (Integer) value);

                } else if (value instanceof Long) {
                    message.setLongProperty(item, (Long) value);

                } else {
                    message.setStringProperty(item, "" + value);
                }
            }


            // Send message
            producer.send(message);

            // Close connection
            // DW: connection could be re-used?
            //connection.close();

            if(LOG.isDebugEnabled()){
                LOG.debug("Message sent with id '" + message.getJMSMessageID() + "'");
            }

        } catch (JMSException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new TransportException("Problem during communcation: " + ex.getMessage(), ex);

        } catch (NamingException ex) {
            LOG.error(ex.getMessage(), ex);
            throw new TransportException(ex.getMessage(), ex);

        } catch (Throwable ex) {
            // I know, bad coding practice, really need it
            LOG.error(ex.getMessage(), ex);
            throw new TransportException(ex.getMessage(), ex);

        } finally {
            // Close all that has been opened. Always.
            closeAll(context, connection, session);
        }
    }
}

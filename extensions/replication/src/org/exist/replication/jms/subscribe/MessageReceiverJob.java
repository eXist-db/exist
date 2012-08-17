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
import org.apache.log4j.Logger;
import org.exist.replication.shared.JmsConnectionExceptionListener;
import org.exist.replication.shared.ClientParameters;
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
    private final static String JOB_NAME = "JMSReceiveJob";
    private String jobName = JOB_NAME;

    @Override
    public void execute(BrokerPool brokerpool, Map<String, ?> params) throws JobException {

        // Get from .xconf file, fill defaults when needed
        SubscriberParameters parameters = new SubscriberParameters();
        parameters.setSingleValueParameters(params);
        parameters.processParameters();
        parameters.fillActiveMQbrokerDefaults();

        Properties contextProps = parameters.getInitialContextProps();

        LOG.debug(parameters.getReport());

        // Setup listeners
        JMSMessageListener jmsListener = new JMSMessageListener(brokerpool);
        ExceptionListener exceptionListener = new JmsConnectionExceptionListener();

        try {
            // Setup context
            Context context = new InitialContext(contextProps);

            // Lookup topic
            Destination destination = (Destination) context.lookup(parameters.getTopic());
            if (!(destination instanceof Topic)) {
                String errorText = "'" + parameters.getTopic() + "' is not a Topic.";
                LOG.error(errorText);
                throw new JobException(JobException.JOB_ABORT_THIS, errorText);
            }

            // Lookup connection factory            
            ConnectionFactory cf = (ConnectionFactory) context.lookup(parameters.getConnectionFactory());

            // Setup connection
            Connection connection = cf.createConnection();

            // Set clientId
            connection.setClientID(parameters.getClientId());

            // TODO DW: should this be configurable?
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // TODO switch
            boolean isDurable = true;
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
            connection.setExceptionListener(exceptionListener);

            // Start it all
            connection.start();

            LOG.info("Sucessfull subscribed '" + parameters.getSubscriberName() + "' to '" + parameters.getTopic() + "'");

        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
            throw new JobException(JobException.JOB_ABORT_THIS, t.getMessage());
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

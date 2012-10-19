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
package org.exist.replication.jms.obsolete;

import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

/**
 * Helperclass for receiving JMS messages
 * 
 * @author Dannes Wessels
 */
public class ResourceReplicator {
    
    private final static Logger LOG = Logger.getLogger(ResourceReplicator.class);

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws NamingException, JMSException, InterruptedException {

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();
        
        try {
            Properties props = new Properties();
            props.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
            props.setProperty(Context.PROVIDER_URL, "tcp://miniserver.local:61616");
            Context context = new InitialContext(props);

            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("ConnectionFactory");

            FileSystemListener myListener = new FileSystemListener();

            Destination destination = (Destination) context.lookup("dynamicTopics/eXistdb");

            LOG.info("Destination=" + destination);

            Connection connection = connectionFactory.createConnection();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer messageConsumer = session.createConsumer(destination);

            messageConsumer.setMessageListener(myListener);

            connection.start();


            LOG.info("Receiver is ready");
            
        } catch (Throwable t) {
            LOG.error(t.getMessage(),t);
        }



    }
}

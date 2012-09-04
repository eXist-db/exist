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

import java.util.Date;
import java.util.Properties;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

/**
 * Helperclass for sending JMS messages
 * 
 * @author Dannes Wessels
 */
public class Sender {
    
    private final static Logger LOG = Logger.getLogger(Sender.class);

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        BasicConfigurator.configure();

        try {

            Properties props = new Properties();
            props.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
            props.setProperty(Context.PROVIDER_URL, "tcp://localhost:61616");
            javax.naming.Context context = new InitialContext(props);


            ConnectionFactory cf = (ConnectionFactory) context.lookup("ConnectionFactory");
            Connection connection = cf.createConnection();

            Destination destination = (Destination) context.lookup("dynamicQueues/eXistdb");

           

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(destination);
            Message message = session.createTextMessage();
            message.setStringProperty("name", "testerdetest " + new Date().toString());


            producer.send(message);
            
            

            connection.close();

            LOG.info("sent " + message.getJMSMessageID());

        } catch (Throwable t) {
            t.printStackTrace();
        }

        System.exit(0);
    }
}

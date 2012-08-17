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
package org.exist.messaging;

import java.util.Enumeration;
import javax.jms.*;
import org.apache.log4j.Logger;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;

/**
 * Handle incoming message by executing function with parameters
 * (JMS config, Metadata, payload)
 * 
 * @author Dannes Wessels
 */
public class MyListener implements MessageListener {

    private final static Logger LOG = Logger.getLogger(MyListener.class);
    private XQueryContext xqcontext;
    private FunctionReference ref = null;

    public MyListener(FunctionReference ref, XQueryContext xqcontext) {
        this.ref = ref;
        this.xqcontext = xqcontext;
        this.ref.setContext(this.xqcontext);
    }

    @Override
    public void onMessage(Message message) {
        try {
            LOG.info(message.getStringProperty("name") + " Id=" + message.getJMSMessageID() + " type=" + message.getJMSType());

            NodeImpl report = createReport(message);
            Item content = null;

            LOG.info("report created");
            
            
            // Get data from message
            // TODO switch based on supplied content-type e.g. element(), 
            // document-node()etc

            if (message instanceof TextMessage) {

                LOG.info("TextMessage");

                String txt = ((TextMessage) message).getText();

                LOG.info(txt);
                content = new StringValue(txt);

            } else if (message instanceof BytesMessage) {

                LOG.info("BytesMessage");

                BytesMessage bm = (BytesMessage) message;

                LOG.info("length=" + bm.getBodyLength());

                byte[] data = new byte[(int) bm.getBodyLength()];

                bm.readBytes(data);

                String txt = new String(data);
                LOG.info("to be converted '" + txt + "'");

                content = new StringValue(txt);

            }


            // Get Meta data from JMS
            // TODO wrap into node structure, flat, or element sequence.
            Enumeration names = message.getPropertyNames();
            for (Enumeration<?> e = names; e.hasMoreElements();) {
                String key = (String) e.nextElement();
                LOG.info(key + " == " + message.getStringProperty(key));
            }

            // Call function
            
            // Construct parameters
            Sequence[] params = new Sequence[3];
            params[0] = new StringValue(".....0"); // report; // report
            params[1] = new StringValue(".....1"); //= report; // meta data
            params[2] = new StringValue(".....2"); //= report; // content
            
            // Execute function
            try {
                LOG.info("execute");

                Sequence ret = ref.evalFunction(null, null, params);
                
                // Never reaches here, due to NPE.
                LOG.info("done");

            } catch (Throwable e) {
                // Catch all issues.
                LOG.error(e.getMessage(), e);
                e.printStackTrace();
            }



        } catch (JMSException ex) {
            LOG.error(ex);
            ex.printStackTrace();
        }
    }

    /**
     * Create messaging results report
     * 
     * TODO shared code, except context (new copied)
     */
    private NodeImpl createReport(Message message) {

        MemTreeBuilder builder = new MemTreeBuilder();
        builder.startDocument();

        // start root element
        int nodeNr = builder.startElement("", "JMS", "JMS", null);

        try {
            String txt = message.getJMSMessageID();
            if (txt != null) {
                builder.startElement("", "MessageID", "MessageID", null);
                builder.characters(message.getJMSMessageID());
                builder.endElement();
            }
        } catch (JMSException ex) {
            LOG.error(ex);
        }

        try {
            String txt = message.getJMSCorrelationID();
            if (txt != null) {
                builder.startElement("", "CorrelationID", "CorrelationID", null);
                builder.characters(message.getJMSCorrelationID());
                builder.endElement();
            }
        } catch (JMSException ex) {
            LOG.error(ex);
        }

        try {
            String txt = message.getJMSType();
            if (txt != null) {
                builder.startElement("", "Type", "Type", null);
                builder.characters(message.getJMSType());
                builder.endElement();
            }
        } catch (JMSException ex) {
            LOG.error(ex);
        }

        // finish root element
        builder.endElement();

        // return result
        return ((DocumentImpl) builder.getDocument()).getNode(nodeNr);


    }
}

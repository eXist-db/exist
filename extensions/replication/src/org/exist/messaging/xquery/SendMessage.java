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
package org.exist.messaging.xquery;

import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.messaging.JmsMessageSender;
import org.exist.messaging.configuration.JmsMessagingConfiguration;
import org.exist.messaging.configuration.MessagingMetadata;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 *
 * @author wessels
 */


public class SendMessage extends BasicFunction {
    
 public final static FunctionSignature signatures[] = {

        new FunctionSignature(
            new QName("send", MessagingModule.NAMESPACE_URI, MessagingModule.PREFIX),
            "Text1",
            new SequenceType[]{
                new FunctionParameterSequenceType("configuration", Type.NODE, Cardinality.EXACTLY_ONE, "text"),
                new FunctionParameterSequenceType("properties", Type.NODE, Cardinality.ZERO_OR_ONE, "text"),
                new FunctionParameterSequenceType("content", Type.ITEM, Cardinality.ZERO_OR_ONE, "Send message to remote server")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "Confirmation message, if present")
        ),

        
    };

    public SendMessage(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        /*
            import module namespace messaging="http://exist-db.org/xquery/messaging"
            at "java:org.exist.messaging.xquery.MessagingModule";

            let $config := 
            <jms>
                <InitialContext>
                    <java.naming.factory.initial>org.apache.activemq.jndi.ActiveMQInitialContextFactory</java.naming.factory.initial>
                    <java.naming.provider.url>tcp://myserver.local:61616</java.naming.provider.url>
                </InitialContext>
                <ConnectionFactory>ConnectionFactory</ConnectionFactory>
                <Destination>dynamicQueues/MyTestQ</Destination>
            </jms>
      
            return
            messaging:send($config, <config/>, "My text"),
            messaging:send($config, <config><key1>value1</key1></config>, doc('/db/data.xml'))
        */
        
        
        
        // Get configuration
	    NodeValue configNode = (NodeValue) args[0].itemAt(0);
        JmsMessagingConfiguration configuration = new JmsMessagingConfiguration();
        configuration.parseDocument(configNode);
        
        // Get additional header
	    NodeValue headersNode = (NodeValue) args[1].itemAt(0);
        MessagingMetadata metaData = new MessagingMetadata();
        metaData.parseDocument(headersNode);
        
        // Get content
        Item content = args[2].itemAt(0);
        
//        if(content instanceof NodeProxy){
//            NodeProxy np = (NodeProxy) content;
//            mmd.add( "url" , np.getDocument().getBaseURI() );
//        }
//        
//        
//        mmd.add("exist.type", Type.getTypeName( content.getType() ));
        
        // Send content
        JmsMessageSender sender = new JmsMessageSender(context);
        
        
        
        NodeImpl result = sender.send(configuration, metaData, content);

        return result;

    }
    
}

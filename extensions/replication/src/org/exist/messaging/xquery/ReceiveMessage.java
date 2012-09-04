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
import org.exist.messaging.JmsMessageReceiver;
import org.exist.messaging.configuration.JmsMessagingConfiguration;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 *
 * @author wessels
 */


public class ReceiveMessage extends BasicFunction {
    
 public final static FunctionSignature signatures[] = {

        new FunctionSignature(
            new QName("receive", MessagingModule.NAMESPACE_URI, MessagingModule.PREFIX),
            "Text1",
            new SequenceType[]{
                new FunctionParameterSequenceType("configuration", Type.NODE, Cardinality.EXACTLY_ONE, "text"),
                new FunctionParameterSequenceType("callback function", Type.FUNCTION_REFERENCE, Cardinality.ZERO_OR_ONE, "text"),
//                new FunctionParameterSequenceType("content", Type.ITEM, Cardinality.ZERO_OR_ONE,
//                        "Send message to remote server")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "Confirmation message, if present")
        ),

        
    };

    public ReceiveMessage(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        /*
            xquery version "1.0";

            import module namespace messaging="http://exist-db.org/xquery/messaging"
            at "java:org.exist.messaging.xquery.MessagingModule";


            declare function local:index-callback($configuration as element(), $properties as element(), $content as item()) {
                util:log("INFO", $content)
            };

            let $config := 
                        <jms>
                            <InitialContext>
                                <java.naming.factory.initial>org.apache.activemq.jndi.ActiveMQInitialContextFactory</java.naming.factory.initial>
                                <java.naming.provider.url>tcp://localhost:61616</java.naming.provider.url>
                            </InitialContext>
                            <ConnectionFactory>ConnectionFactory</ConnectionFactory>
                            <Destination>dynamicQueues/MyTestQ</Destination>
                        </jms>

            let $callback := util:function(xs:QName("local:index-callback"), 3)

            return
            messaging:receive($config, $callback)
        */
        
        
        
        // Get configuration
	    NodeValue configNode = (NodeValue) args[0].itemAt(0);
        JmsMessagingConfiguration jmc = new JmsMessagingConfiguration();
        jmc.parseDocument(configNode);
        
//        // Get additional header
//	    NodeValue headersNode = (NodeValue) args[1].itemAt(0);
//        MessagingMetadata mmd = new MessagingMetadata();
//        mmd.parseDocument(headersNode);
        
        // Get function reference
        FunctionReference ref = (FunctionReference) args[1].itemAt(0);
        
        
        // Get content
//        Item content = args[2].itemAt(0);
        
//        if(content instanceof NodeProxy){
//            NodeProxy np = (NodeProxy) content;
//            mmd.add( "url" , np.getDocument().getBaseURI() );
//        }
//        
//        
//        mmd.add("exist.type", Type.getTypeName( content.getType() ));
        
        // Send content
        JmsMessageReceiver sender = new JmsMessageReceiver(context);
        
        
        NodeImpl result = sender.receive(jmc, ref);

        return result;

    }
    
}

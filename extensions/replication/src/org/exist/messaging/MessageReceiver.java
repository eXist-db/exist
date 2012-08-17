package org.exist.messaging;

import org.exist.memtree.NodeImpl;
import org.exist.messaging.configuration.JmsMessagingConfiguration;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.FunctionReference;

/**
 *
 * @author wessels
 */
public interface MessageReceiver {

    public NodeImpl receive(JmsMessagingConfiguration jmc, FunctionReference ref) throws XPathException ;
}

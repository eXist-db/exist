package org.exist.messaging;

import org.exist.memtree.NodeImpl;
import org.exist.messaging.configuration.JmsMessagingConfiguration;
import org.exist.messaging.configuration.MessagingMetadata;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;

/**
 *
 * @author wessels
 */
public interface MessageSender {

    public NodeImpl send(JmsMessagingConfiguration jmc, MessagingMetadata mmd, Item content) throws XPathException ;
}

//$Id$
package org.exist.cluster;

import org.apache.log4j.Logger;
import org.jgroups.Channel;
import org.jgroups.Message;
import org.jgroups.blocks.MessageDispatcher;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 *         Date: Aug 30, 2004
 *         Time: 4:54:51 PM
 *         Revision $Revision$
 */
public class ClusterClient {
    
	private static final Logger log = Logger.getLogger(ClusterClient.class);
    Channel channel;
    MessageDispatcher disp;


    public ClusterClient() throws ClusterException  {
        this( ClusterChannel.getChannel() );
    }
    /**
     * Create a ClusterClient on a given channel
     * @param channel
     * @throws ClusterException
     */
    public ClusterClient(Channel channel) throws ClusterException  {
        this.channel = channel;
        disp = new MessageDispatcher(channel, null, null, null);        
    }


    public void start(String[] args) throws Exception {



        if (args.length < 3) {
            System.out.println("Usage: ");
            System.out.println("args: ");
            System.out.println("0: command (save,delete) ");
            System.out.println("1: filename ");
            System.out.println("2: xml content ");

            return;
        }


        if (args[0].equals("save")) {
            ClusterEvent event = new StoreClusterEvent(args[2], "/db", args[1]);
            sendClusterEvent(event);
        }
        else {
            ClusterEvent event = new RemoveClusterEvent(args[1], "/db");
            sendClusterEvent(event);
        }
    }


    /**
     * Send a generic cluster event to the jgroup.
     * @param event
     * @throws ClusterException
     */
    public void sendClusterEvent(ClusterEvent event) throws ClusterException {
        log.info("ClusterClient: Publishing Event");
        int eventCode = event.hashCode();
        if (!ClusterChannel.hasToBePublished( String.valueOf(eventCode) )) {
            return;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(event);
            bos.flush();
            byte[] b = bos.toByteArray();

            ClusterChannel.accountEvent( String.valueOf(eventCode));

            disp.send( new Message(null, null, b)  );
            log.info("ClusterClient: Event has been submitted");

        } catch (Exception e) {
            e.printStackTrace();
            throw new ClusterException(e);
        }
    }


}

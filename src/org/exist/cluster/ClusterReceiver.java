//$Id$
package org.exist.cluster;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka mdanieli
 *         Date: Aug 31, 2004
 *         Time: 3:00:25 PM
 *         Revision $Revision$
 */

import org.jgroups.*;
import org.jgroups.util.*;
import org.jgroups.blocks.*;
import org.apache.log4j.Logger;

import java.io.*;

public class ClusterReceiver implements RequestHandler, MessageListener, Runnable {


    private boolean running=true;


    static Logger log = Logger.getLogger( ClusterReceiver.class );

    Channel channel;
    MessageDispatcher disp;
    RspList rsp_list;
    String props = "UDP";

    /**
     * Stop serving cluster events on the channel
     *
     */
    public void stop(){

        channel.disconnect();
        running = false;

    }

    public void run()  {

        try {
            log.info("Starting JavaGroup");
            channel = new JChannel( ClusterConfiguration.getProtocolStack() );
            channel.connect( ClusterChannel.EXIST_GROUP );
            disp = new MessageDispatcher(channel, this, null, this);
            log.info("Started JavaGroup");

            while (running) {
                Thread.sleep(100);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    public void receive(Message msg) {

        byte[] b = msg.getBuffer();

        if (b == null) return;


        log.info("Cluster Event Received:" + new String(b));
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(b);
            ObjectInputStream ois = new ObjectInputStream(bis);
            ClusterEvent ce = (ClusterEvent) ois.readObject();
            if ( ! ClusterChannel.hasToBePublished( String.valueOf( ce.hashCode() ))){
                log.info("REENTRANT Event found: must not be delivered!" );
                return;
            }
            else {
                ce.execute();
                ClusterChannel.accountEvent(String.valueOf(ce.hashCode()));
            }

        } catch (Throwable e) {
        }

    }


    public byte[] getState() {
        return b;
    }

    byte[] b = null;

    public void setState(byte[] b) {
        this.b = b;
    }

    public Object handle(Message msg) {        
        return new String("Success !");
    }


}

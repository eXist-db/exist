//$Id$
package org.exist.cluster;

import java.util.Vector;


import org.apache.log4j.Logger;
import org.jgroups.Channel;
import org.jgroups.ChannelException;
import org.jgroups.JChannel;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka mdanieli
 *         Date: 13-dic-2004
 *         Time: 18.09.08
 *         Revision $Revision$
 */
public class ClusterChannel {
	
    private static Logger log = Logger.getLogger(ClusterChannel.class);
    private static Channel channel = initChannel();
    public static final String EXIST_GROUP = "exist-replication-group";
    public static Vector incomingEvents = new Vector();



    public static Channel getChannel() throws ClusterException {
        return channel;
    }
    
    private static final Channel initChannel(){
    	try {
        	   log.info("Javagroups: connecting to channel");
               Channel channel = new JChannel(ClusterConfiguration.getProtocolStack());
               channel.connect( ClusterChannel.EXIST_GROUP );
               return channel;            
        } catch (ChannelException e) {
            log.fatal(e);            
        }        
        return null;
    }

    public static boolean hasToBePublished(String event) {
        //TODO fix event management
        return !incomingEvents.contains(event);
        //return true;
    }

    public static void accountEvent(String event) {

        incomingEvents.addElement(event);

    }


   
}

//$Id$
package org.exist.cluster;

import java.util.Vector;

import org.apache.log4j.Logger;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka mdanieli
 * @author Nicola Breda aka maiale
 *         Date: 05-aug-2005
 *         Time: 18.09.08
 *         Revision $Revision$
 */
public class ClusterChannel {

    private static Logger log = Logger.getLogger(ClusterChannel.class);
    public static final String EXIST_GROUP = "exist-replication-group";
    public static Vector incomingEvents = new Vector();


    public static boolean hasToBePublished(String event) {
        return !incomingEvents.contains(event);
    }

    public static void accountEvent(String event) {
        incomingEvents.addElement(event);
    }


    public static void removeEvent(String code) {
        incomingEvents.remove(code);
    }
}


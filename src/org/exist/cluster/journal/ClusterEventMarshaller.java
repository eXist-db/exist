//$Id$
package org.exist.cluster.journal;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.MarshalException;

import org.exist.cluster.ClusterEvent;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

/**
 * Created by Nicola Breda.
 *
 * @author Nicola Breda aka maiale
 * @author David Frontini aka spider
 *         Date: 05-aug-2005
 *         Time: 18.09.08
 *         Revision $Revision$
 */
public class ClusterEventMarshaller {

    public static byte[] marshall(ClusterEvent event) throws MarshalException {
        try {
            ByteArrayOutputStream bb = new ByteArrayOutputStream();
            ObjectOutputStream o = new ObjectOutputStream(bb);
            o.writeObject(event);
            o.close();
            return bb.toByteArray();
        } catch (Exception e) {
            throw new MarshalException("Error marshalling event " + event.getId());
        }
    }

    public static ClusterEvent unmarshall(byte[] data) throws MarshalException {
        try {
            ByteArrayInputStream bb = new ByteArrayInputStream(data);
            ObjectInputStream o = new ObjectInputStream(bb);
            Object event = o.readObject();
            o.close();
            return (ClusterEvent) event;
        } catch (Exception e) {
            throw new MarshalException("Error unmarshalling event ");
        }
    }


}

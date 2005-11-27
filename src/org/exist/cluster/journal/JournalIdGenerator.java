//$Id$
package org.exist.cluster.journal;

import java.util.HashMap;

import org.exist.cluster.ClusterEvent;

/**
 * Manage the generation of the unique journal Id
 * Created by Nicola Breda.
 *
 * @author Nicola Breda aka maiale
 * @author David Frontini aka spider
 *         Date: 05-aug-2005
 *         Time: 18.09.08
 *         Revision $Revision$
 */
public class JournalIdGenerator {
    public static int MAX_STORED_INDEX = 65000;

    private int lastId = ClusterEvent.NO_EVENT;
    private int counter = 0;

    private HashMap idInUse = new HashMap();

    public JournalIdGenerator(JournalManager journal, int maxItem) {
        System.out.println("MAX STORE IN ID GENERATOR = " + maxItem);
        lastId = journal.getMaxIdSaved();
        counter = journal.getCounter();
        MAX_STORED_INDEX = maxItem;
    }

    public synchronized int[] getNextData(String address) {
        lastId = lastId + 1;

        if (lastId > MAX_STORED_INDEX) {
            lastId = 0;
            counter++;
        }

        idInUse.put("" + lastId, address);


        return new int[]{lastId, counter};
    }

    public void setLastId(int lastId) {
        this.lastId = lastId;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public void releaseId(int id) {
        idInUse.remove("" + id);
    }

    public synchronized void increaseId(int id, int counter) {  //TODO pensare meglio questa parte -- rimane il problema della rotazione
        if ((id > lastId) || (this.counter!=counter)){
            lastId = id;
            this.counter = counter;
        }
    }

    public int[] getData() {
        return new int[]{lastId,counter};
    }

    public synchronized void shiftId(int shift) {
        lastId += shift;
        if(lastId>MAX_STORED_INDEX){
            lastId -= MAX_STORED_INDEX;
            counter++;
        }
    }
}

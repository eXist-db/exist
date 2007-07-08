//$Id$
package org.exist.cluster.journal;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.exist.cluster.ClusterEvent;
import org.exist.cluster.ClusterException;
import org.exist.util.Configuration;
import org.exist.xquery.Constants;

/**
 * Manage the Journal
 * Created by Nicola Breda.
 *
 * @author Nicola Breda aka maiale
 * @author David Frontini aka spider
 *         Date: 05-aug-2005
 *         Time: 18.09.08
 *         Revision $Revision$
 */
public class JournalManager {
	
	public static final String JOURNAL_DIR_ATTRIBUTE = "journalDir";
	public static final String CLUSTER_JOURNAL_MAXSTORE_ATTRIBUTE = "journalMaxItem";
	public static final String CLUSTER_JOURNAL_SHIFT_ATTRIBUTE = "journalIndexShift";
	
	public static final String PROPERTY_JOURNAL_DIR = "cluster.journalDir";
	public static final String PROPERTY_CLUSTER_JOURNAL_MAXSTORE = "cluster.journal.maxStore";
	public static final String PROPERTY_CLUSTER_JOURNAL_SHIFT = "cluster.journal.shift";

    private static final String JOURNAL_INDEX_FILE = "jei.jbx";
    private static final String JOURNAL_STORAGE_FILE_EXTENSION = ".jbx";
    public static int JOURNAL_STORAGE_FILE_MAX_SIZE = 1024 * 1024 * 10;  //TODO renderlo configurabile
    private static final int JOURNAL_INDEX_TRUNK_SIZE = 5 * 4;  //item[ID,START,END,COUNT]
    private static final int JOURNAL_INDEX_FIRST_TRUNK_SIZE = 4 + 4 + 4; //header size []
    public static int REALIGN_MAX_BLOCK_SIZE = 20;

    private static Logger log = Logger.getLogger(JournalManager.class);

    private File dir;
    private File indexFile;

    private boolean journalDisabled = false;
    private boolean isNewJournal = false;

    private int lastIdSaved = ClusterEvent.NO_EVENT;
    private int counter = 1;
    private int maxIdSaved = ClusterEvent.NO_EVENT;

    TreeSet queue = new TreeSet(new EventComparator());

    public JournalManager(Configuration conf) {
        String dirName = (String) conf.getProperty(PROPERTY_JOURNAL_DIR); //retrieve journal folder
        if (dirName == null) { //disable journal if non folder found
            journalDisabled = true;
            return;
        }
        dir = new File(dirName);
        if (!dir.exists()) { //if journal folder doesn't exist --> create it.
            dir.mkdirs();
        }
        indexFile = new File(dir, JOURNAL_INDEX_FILE);
        if (!indexFile.exists()) { //if the journal file doesn't exist create it and flag isNewJournal
            try {
                indexFile.createNewFile();
                writeInitialHeader();
                isNewJournal = true;
            } catch (IOException e) {
                log.error("Error creating index file... disabling jornal");
                journalDisabled = true;
            }
        } else {
            int[] header = getHeaderData();
            lastIdSaved = header[0];
            maxIdSaved = header[1];
            counter = header[2];
            checkNewJournal();
        }
    }

    private void checkNewJournal() {
        if (lastIdSaved == ClusterEvent.NO_EVENT)
            isNewJournal = true;
    }

    private void writeInitialHeader() {
        try {
            RandomAccessFile raf = new RandomAccessFile(indexFile, "rws");
            raf.writeInt(lastIdSaved);
            raf.writeInt(maxIdSaved);
            raf.writeInt(counter);
            raf.close();
        } catch (Exception e) {
            throw new RuntimeException("Error create initila header");
        }

    }

    /*
       TODO ... gestire la cancellazione di file non piu' referenziati
    */

    public int getLastIdSaved() {
        if (lastIdSaved != ClusterEvent.NO_EVENT)
            return lastIdSaved;
        else if (isNewJournal||journalDisabled)
            return ClusterEvent.NO_EVENT;
        else
            return getHeaderData()[0];
    }

    public int getMaxIdSaved() {
        if (maxIdSaved != ClusterEvent.NO_EVENT)
            return maxIdSaved;
        else if (isNewJournal||journalDisabled)
            return ClusterEvent.NO_EVENT;
        else
            return getHeaderData()[1];
    }

    public int getCounter() {
        if (counter != ClusterEvent.NO_EVENT)
            return counter;
        else if (isNewJournal||journalDisabled)
            return 1;
        else
            return getHeaderData()[2];
    }

    private int[] getHeaderData() {
        int[] header = new int[3];
        try {
            RandomAccessFile raf = new RandomAccessFile(indexFile, "r");
            header[0] = raf.readInt();
            header[1] = raf.readInt();
            header[2] = raf.readInt();
            raf.close();
        } catch (Exception e) {
            throw new RuntimeException("Error during retrieving last id");
        }

        return header;
    }

    public boolean isProcessed(ClusterEvent event) {

        if(journalDisabled)
            return false;

        int id = event.getId();

        if (queue.contains(event))
            return true;

        try {
            RandomAccessFile raf = new RandomAccessFile(indexFile, "r");
            raf.seek(JOURNAL_INDEX_FIRST_TRUNK_SIZE + id * JOURNAL_INDEX_TRUNK_SIZE + 16);

            //[ ID(4byte) |  START_DATA_FILE(4byte) | END_DATA_FILE(4 bytes) | FILE_NAME(4 bytes)| CONUTER(4 bytes)]
            int counter = raf.readInt();
            raf.close();

            return event.getCounter() == counter;

        } catch (Exception e) {
            return false;
        }

    }

    public void squeueEvent() throws ClusterException {
        if (journalDisabled) {
            log.info("Error persisting data..... journal disabled");
            return;
        }

        boolean done = false;
        while (queue.size() > 0) {
            done = true;
            ClusterEvent event = (ClusterEvent) queue.first();
            saveEvent(event);
            queue.remove(event);
        }

        if (done) {
            int[] header = getHeaderData();
            System.out.println("IN SYNC last = " + header[0] + " MAX = " + header[1] + " COUNTER = " + header[2]);
        }
    }

    public void enqueEvent(ClusterEvent event) throws ClusterException {
        if (journalDisabled) {
            log.info("Error persisting data..... journal disabled");
            return;
        }

        int id = event.getId();
        if (id == ClusterEvent.NO_EVENT)
            throw new ClusterException("Error in Journal managment... no id found in event");

        queue.add(event);
    }

    private void saveEvent(ClusterEvent event) {
        int id = event.getId();
        int counter = event.getCounter();

        try {
            byte[] eventBytes = ClusterEventMarshaller.marshall(event);
            int start = 0;
            int end = eventBytes.length;
            int file = 0;

            RandomAccessFile raf = new RandomAccessFile(indexFile, "rws");
            if (!isNewJournal) {
                //read previous ID
                int prev = lastIdSaved;

                raf.seek(JOURNAL_INDEX_FIRST_TRUNK_SIZE + prev * JOURNAL_INDEX_TRUNK_SIZE);
                raf.readInt();
                raf.readInt();
                int prevEnd = raf.readInt();
                int prevFile = raf.readInt();

                start = prevEnd;
                end = prevEnd + eventBytes.length;
                file = prevFile;

                if (prevEnd > JOURNAL_STORAGE_FILE_MAX_SIZE) {
                    start = 0;
                    end = eventBytes.length;
                    file++;
                }

            } else {
                isNewJournal = false;
            }

            writeDataFile(file, start, eventBytes);

            raf.seek(JOURNAL_INDEX_FIRST_TRUNK_SIZE + id * JOURNAL_INDEX_TRUNK_SIZE);
            raf.writeInt(id);
            raf.writeInt(start);
            raf.writeInt(end);
            raf.writeInt(file);
            raf.writeInt(counter);

            //write last ID
            lastIdSaved = id;

            System.out.println(">>>>>>>>>> ID " + id);
            System.out.println(">>>>>>>>>> COUNTER " + counter);
            if (((id > maxIdSaved) && (this.counter == counter)) || (this.counter == counter - 1)) {
                maxIdSaved = id;
            }

            if (this.counter == counter - 1) {
                this.counter = counter;
            }

            System.out.println("********** HEADER ID = " + lastIdSaved);
            System.out.println("********** HEADER MAXID = " + maxIdSaved);
            System.out.println("********** HEADER COUNTER = " + this.counter);

            raf.seek(0);


            raf.writeInt(lastIdSaved);
            raf.writeInt(maxIdSaved);
            raf.writeInt(this.counter);
            raf.close();

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error writing journal file... for ID : " + id);
            throw new RuntimeException("Error writing journal file for ID : " + id, e);
        }
    }

    private void writeDataFile(int file, int start, byte[] eventBytes)
            throws IOException {
        File storage = new File(dir, file + JOURNAL_STORAGE_FILE_EXTENSION);
        RandomAccessFile store = new RandomAccessFile(storage, "rws");
        store.seek(start);
        store.write(eventBytes);
        store.close();
    }

    public synchronized ClusterEvent read(int id) {
        try {
            RandomAccessFile raf = new RandomAccessFile(indexFile, "r");
            raf.seek(JOURNAL_INDEX_FIRST_TRUNK_SIZE + id * JOURNAL_INDEX_TRUNK_SIZE);

            //[ ID(4byte) |  START_DATA_FILE(4byte) | END_DATA_FILE(4 bytes) | FILE_NAME(4 bytes)| CONUTER(4 bytes)]
            raf.readInt();
            int start = raf.readInt();
            int end = raf.readInt();
            int file = raf.readInt();
            raf.close();

            return readFromStorage(end, start, file);

        } catch (Exception e) {
            log.error("Error reading journal file ... " + e);
            throw new RuntimeException("Error rading journal file " + e);
        }

    }

    private ClusterEvent readFromStorage(int end, int start, int file) throws IOException {
        byte[] eventBytes = new byte[end - start];

        File storage = new File(dir, file + JOURNAL_STORAGE_FILE_EXTENSION);
        RandomAccessFile store = new RandomAccessFile(storage, "r");

        store.seek(start);
        store.read(eventBytes);
        store.close();

        return ClusterEventMarshaller.unmarshall(eventBytes);
    }

    public ArrayList getNextEvents(int[] header, int[] myHeader, Integer start) {

        if(journalDisabled)
            return null;

        System.out.println("Get next events : lastIdSaved " + header[0] + " maxId " + header[1] + " counter:" + header[2]);
        System.out.println("Get next events saved : lastIdSaved " + myHeader[0] + " maxId " + myHeader[1] + " counter:" + myHeader[2]);
        if (header[0] == myHeader[0] && header[1] == myHeader[1] && header[2] == myHeader[2]) {
            System.out.println("Return empty arraylist");
            return new ArrayList(); //same header
        }
        System.out.println("Start :" + start.intValue());
        if (start.intValue() == -1) {
            return getStart(header[0], myHeader);
        } else {
            return getEvents(start.intValue(), myHeader);
        }
    }

    private ArrayList getEvents(int last, int[] myHeader) {

        ArrayList events = new ArrayList();

        try {
            RandomAccessFile raf = new RandomAccessFile(indexFile, "r");
            int pos = last;
            System.out.println("INITIAL POS = " + pos);

            if (last == myHeader[1])
                return null; //the max saved id is reached
            while (true) {
                raf.seek(JOURNAL_INDEX_FIRST_TRUNK_SIZE + pos * JOURNAL_INDEX_TRUNK_SIZE + 4);
                int start = raf.readInt();
                int end = raf.readInt();
                int file = raf.readInt();
                int count = raf.readInt();
                if ((pos <= myHeader[1] && count == myHeader[2]) || (pos > myHeader[1] && count == myHeader[2] - 1)) {
                    ClusterEvent event = readFromStorage(end, start, file);
                    events.add(event);
                    System.out.println("Add element " + event.getId());
                }

                if (events.size() >= REALIGN_MAX_BLOCK_SIZE)
                    break;

                if (pos == myHeader[1])
                    break;

                pos += 1;
                int size = (((int) raf.length() - 12) / 20) - 1;
                if (pos > size) {
                    pos = 0;
                }

            }

            raf.close();
            return events.size() != 0 ? events : null;

        } catch (Exception e) {
            log.error("Error reading journal file ... " + e);
            throw new RuntimeException("Error rading journal file " + e);
        }

    }

    private synchronized ArrayList getStart(int last, int[] myHeader) {
        ArrayList events = new ArrayList();
        try {
            int pos = last < 0 ? 0 : last;
            final int c = myHeader[2];
            final int m = myHeader[1];

            RandomAccessFile raf = new RandomAccessFile(indexFile, "r");
            while (true) {
                raf.seek(JOURNAL_INDEX_FIRST_TRUNK_SIZE + pos * JOURNAL_INDEX_TRUNK_SIZE);
                int id = raf.readInt();
                int start = raf.readInt();
                int end = raf.readInt();
                int file = raf.readInt();
                int count = raf.readInt();

                if ((pos < m && count == c) || (pos > m && count == c - 1)) {
                    ClusterEvent event = readFromStorage(end, start, file);
                    events.add(0, event);
                    System.out.println("Add element " + event.getId());
                }

                if (events.size() >= REALIGN_MAX_BLOCK_SIZE)
                    break;


                pos -= 1;
                System.out.println("Pos : " + pos);
                System.out.println("COUNER =  " + c);
                if (pos < 0 && c != 1) {
                    pos = (((int) raf.length() - 12) / 20) - 1;
                    if (pos <= m)
                        break;
                } else if (pos < 0) {
                    break;
                }

                if (pos == myHeader[1])
                    break;


            }

            System.out.println("EXITING");

            raf.close();


            return events.size() != 0 ? events : getEvents(last < 0 ? 0 : last, myHeader);

        } catch (Exception e) {
            log.error("Error reading journal file ... " + e);
            throw new RuntimeException("Error reading journal file ", e);
        }
    }

    private class EventComparator implements Comparator {
        public int compare(Object o, Object o1) {
            if (!(o instanceof ClusterEvent))
                return Constants.INFERIOR;
            if (!(o1 instanceof ClusterEvent))
                return Constants.SUPERIOR;

            ClusterEvent ev = (ClusterEvent) o;
            ClusterEvent ev1 = (ClusterEvent) o1;

            int counter = ev.getCounter();
            int counter1 = ev1.getCounter();

            int id = ev.getId();
            int id1 = ev1.getId();


            if (counter == counter1)
                return id - id1;

            return counter - counter1;

        }
    }
}

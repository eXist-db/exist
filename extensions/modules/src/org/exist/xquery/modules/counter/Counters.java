package org.exist.xquery.modules.counter;

import java.io.*;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;

/**
 * @author Jasper Linthorst (jasper.linthorst@gmail.com)
 *
 */
public class Counters implements RawBackupSupport {

    private final static Logger LOG = LogManager.getLogger(Counters.class);
    
    private static volatile Counters instance;

    public final static String COUNTERSTORE = "counters";
    public final static String DELIMITER = ";";

    private File store = null;
    private Hashtable<String, Long> counters = new Hashtable<String, Long>();

    private Counters(String dataDir) throws EXistException {

        store = new File(dataDir, COUNTERSTORE);

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(store));

        } catch (FileNotFoundException e) {
            try {
                store.createNewFile();
                br = new BufferedReader(new FileReader(store));
            } catch (IOException e1) {
                // Failed to create an empty file, probably no write permission..
                throw new EXistException("Unable to create counter store file.");
            }
        }

        try {
            if (store.exists() && store.canRead()) {
                String line = "";

                while ((line = br.readLine()) != null) {
                    //Use ; as a DELIMITER, counter names must be tested and rejected when they contain this character!
                    String[] tokens = line.split(DELIMITER);
                    counters.put(tokens[0], Long.parseLong(tokens[1]));
                }

                br.close();
            }

        } catch (IOException e) {
            throw new EXistException("IOException occurred when reading counter store file.");

        } catch (NumberFormatException e) {
            throw new EXistException("Corrupt counter store file: " + store.getAbsolutePath());

        } catch (ArrayIndexOutOfBoundsException e) {
            throw new EXistException("Corrupt counter store file: " + store.getAbsolutePath());
        }
    }

    /**
     *  Get singleton of Counters object.
     */
    public static Counters getInstance(String dataDir) throws EXistException {
        if (instance == null) {
            LOG.debug("Initializing counters.");
            instance = new Counters(dataDir);
        }
        return instance;
    }

    public static Counters getInstance() throws EXistException {
        if (instance == null) {
            instance = getInstance(COUNTERSTORE);
        }
        return instance;
    }

    /**
     * Creates a new Counter, initializes it to 0 and returns the current value in a long.
     * 
     * @param counterName
     * @return the initial value of the newly created counter
     * @throws EXistException
     */
    public long createCounter(String counterName) throws EXistException {
        return createCounter(counterName, (long) 0);
    }

    /**
     * Creates a new Counter, initializes it to initValue and returns the current value in a long.
     * If there already is a counter with the same name, the current value of this counter is returned.
     * 
     * @param counterName
     * @param initValue
     * @return the current value of the named counter
     * @throws EXistException 
     */
    public synchronized long createCounter(String counterName, long initValue) throws EXistException {
        if (counters.containsKey(counterName)) {
            return counters.get(counterName);
        } else {
            counters.put(counterName, initValue);

            try {
                serializeTable();
            } catch (FileNotFoundException e) {
                throw new EXistException("Unable to save to counter store file.");
            }

            return counters.get(counterName);
        }
    }

    /**
     * Removes a counter by the specified name.
     * 
     * @param counterName
     * @return true if the counter is removed
     * @throws EXistException 
     */
    public synchronized boolean destroyCounter(String counterName) throws EXistException {
        if (counters.containsKey(counterName)) {
            counters.remove(counterName);

            try {
                serializeTable();
            } catch (FileNotFoundException e) {
                throw new EXistException("Unable to remove counter from counter store file.");
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * Retrieves the next value of a counter (specified by name).
     * 
     * @param counterName
     * @return the next counter value or -1 if the counter does not exist.
     * @throws EXistException
     */
    public synchronized long nextValue(String counterName) throws EXistException {
        if (!counters.containsKey(counterName)) {
            return -1;
        }

        long c = counters.get(counterName);
        c++;

        counters.put(counterName, c);

        try {
            serializeTable();
        } catch (FileNotFoundException e) {
            throw new EXistException("Unable to save to counter store file.");
        }

        return c;
    }

    /**
     * Returns all available counters in a Set of Strings.
     * 
     * @return all available counters in a Set of Strings
     */
    public Set<String> availableCounters() {
        return counters.keySet();
    }

    /**
     * Serializes the Map with counters to the filesystem.
     * 
     * @throws FileNotFoundException
     */
    private synchronized void serializeTable() throws FileNotFoundException {

        PrintWriter p = new PrintWriter(store);
        Iterator<String> i = counters.keySet().iterator();

        String k = "";
        long v = 0;

        while (i.hasNext()) {
            k = (String) i.next();
            v = counters.get(k);
            p.println(k + DELIMITER + v);
        }

        p.close();
    }

    @Override
    public void backupToArchive(RawDataBackup backup) throws IOException {
        if (!store.exists())
            return;
        OutputStream os = backup.newEntry(store.getName());
        InputStream is = new FileInputStream(store);
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
        is.close();
        os.close();
        backup.closeEntry();
    }

}

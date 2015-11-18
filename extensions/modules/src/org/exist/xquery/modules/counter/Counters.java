package org.exist.xquery.modules.counter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.RawBackupSupport;
import org.exist.util.FileUtils;

/**
 * @author Jasper Linthorst (jasper.linthorst@gmail.com)
 *
 */
public class Counters implements RawBackupSupport {

    private final static Logger LOG = LogManager.getLogger(Counters.class);
    
    private static volatile Counters instance;

    public final static String COUNTERSTORE = "counters";
    public final static String DELIMITER = ";";

    private Path store = null;
    private Map<String, Long> counters = new Hashtable<>();

    private Counters(final Optional<Path> dataDir) throws EXistException {
        this.store = FileUtils.resolve(dataDir, COUNTERSTORE);
        loadStore();
    }

    /**
     * Loads data from the on-disk counter store
     */
    private void loadStore() throws EXistException {
        try {
            if(Files.exists(store)) {
                try(final BufferedReader br = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
                    String line = "";
                    while ((line = br.readLine()) != null) {
                        //Use ; as a DELIMITER, counter names must be tested and rejected when they contain this character!
                        final String[] tokens = line.split(DELIMITER);

                        try {
                            counters.put(tokens[0], Long.parseLong(tokens[1]));
                        } catch (final NumberFormatException e) {
                            throw new EXistException("Corrupt counter store file: " + store.toAbsolutePath().toString());
                        }
                    }
                }
            }
        } catch (final IOException e) {
            throw new EXistException("IOException occurred when reading counter store file.");
        }
    }

    /**
     *  Get singleton of Counters object.
     */
    public static Counters getInstance(final Path dataDir) throws EXistException {
        if (instance == null) {
            LOG.debug("Initializing counters.");
            instance = new Counters(Optional.ofNullable(dataDir));
        }
        return instance;
    }

    public static Counters getInstance() throws EXistException {
        return getInstance(null);
    }

    /**
     * Creates a new Counter, initializes it to 0 and returns the current value in a long.
     * 
     * @param counterName
     * @return the initial value of the newly created counter
     * @throws EXistException
     */
    public long createCounter(final String counterName) throws EXistException {
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
    public synchronized long createCounter(final String counterName, final long initValue) throws EXistException {
        if (counters.containsKey(counterName)) {
            return counters.get(counterName);
        } else {
            counters.put(counterName, initValue);

            try {
                serializeTable();
            } catch (final IOException e) {
                throw new EXistException("Unable to save to counter store file.", e);
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
    public synchronized boolean destroyCounter(final String counterName) throws EXistException {
        if (counters.containsKey(counterName)) {
            counters.remove(counterName);

            try {
                serializeTable();
            } catch (final IOException e) {
                throw new EXistException("Unable to remove counter from counter store file.", e);
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
    public synchronized long nextValue(final String counterName) throws EXistException {
        if (!counters.containsKey(counterName)) {
            return -1;
        }

        long c = counters.get(counterName);
        c++;

        counters.put(counterName, c);

        try {
            serializeTable();
        } catch (final IOException e) {
            throw new EXistException("Unable to save to counter store file.", e);
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
     * @throws IOException
     */
    private synchronized void serializeTable() throws IOException {
        try(final PrintWriter pw = new PrintWriter(Files.newBufferedWriter(store, StandardCharsets.UTF_8))) {
            for(final Map.Entry<String, Long> counter : counters.entrySet()) {
                pw.println(counter.getKey() + DELIMITER + counter.getValue().toString());
            }
        }
    }

    @Override
    public void backupToArchive(final RawDataBackup backup) throws IOException {
        if (!Files.exists(store)) {
            return;
        }

        try(final OutputStream os = backup.newEntry(FileUtils.fileName(store))) {
            Files.copy(store, os);
        } finally {
            backup.closeEntry();
        }
    }
}

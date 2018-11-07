package org.exist;

import org.exist.storage.BrokerPool;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.Value;
import org.exist.util.*;
import org.exist.xquery.TerminatedException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BTreeTest {

    private final static byte BTREE_TEST_FILE_ID = 0x7F;
    private final static short BTREE_TEST_FILE_VERSION = Short.MIN_VALUE;

    private Path file;

    private BrokerPool pool = null;

    public BTreeTest() {
        file = Paths.get(System.getProperty("exist.home", ".")).resolve("test/test.dbx");
        try {
            Configuration config = new Configuration();

            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        } catch (DatabaseConfigurationException e) {
            e.printStackTrace();
        } catch (EXistException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        pool.shutdown(false);
    }

    public void create(int count) throws DBException, IOException {
        FileUtils.deleteQuietly(file);
        BTree btree = null;
        try {
            btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file);
            btree.create((short) -1);

            String prefixStr = "KEY";
            for (int i = 1; i <= count; i++) {
                Value value = new Value(prefixStr + Integer.toString(i));
                btree.addValue(value, i);
            }
            btree.flush();

            try(final OutputStreamWriter writer = new OutputStreamWriter(System.out)) {
                btree.dump(writer);
                writer.flush();
            }
        } finally {
            if (btree != null) {
                btree.close();
            }
        }
    }

    public void rebuild() throws DBException, IOException, TerminatedException {
        BTree btree = null;
        try {
            System.out.println("Loading btree ...");
            btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file);
            btree.open((short)-1);

            System.out.println("Rebuilding ...");
            btree.rebuild();

            try(final OutputStreamWriter writer = new OutputStreamWriter(System.out)) {
                btree.dump(writer);
                writer.flush();
            }
        } finally {
            if (btree != null) {
                btree.close();
            }
        }
    }

    public void read(int count) throws DBException, IOException, TerminatedException {
        BTree btree = null;
        try {
            System.out.println("Loading btree ...");
            btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file);
            btree.open((short)-1);

            String prefixStr = "KEY";
            for (int i = 1; i <= count; i++) {
                Value value = new Value(prefixStr + Integer.toString(i));
                long r = btree.findValue(value);
                if (r == -1) {
                    System.out.println("Key not found: " + i);
                }
            }
        } finally {
            if (btree != null) {
                btree.close();
            }
        }
    }

    public static void main(String[] args) {
        int count = Integer.parseInt(args[0]);
        String command = args.length == 2 ? args[1] : null;

        BTreeTest test = new BTreeTest();

        try {
            if (command == null) {
                test.create(count);
                test.rebuild();
                test.read(count);
            } else if ("read".equals(command)) {
                test.read(count);
            } else if ("create".equals(command)) {
                test.create(count);
            } else if ("rebuild".equals(command)) {
                test.rebuild();
            }
        } catch (DBException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TerminatedException e) {
            e.printStackTrace();
        } finally {
            test.shutdown();
        }
    }
}
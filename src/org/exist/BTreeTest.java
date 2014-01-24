package org.exist;

import org.exist.storage.BrokerPool;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.Value;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.UTF8;
import org.exist.util.XMLString;
import org.exist.xquery.TerminatedException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class BTreeTest {

    private File file;

    private BrokerPool pool = null;

    public BTreeTest() {
        file = new File(System.getProperty("exist.home", ".") + "/test/test.dbx");
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
        file.delete();
        BTree btree = null;
        try {
            btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.create((short) -1);

            String prefixStr = "KEY";
            for (int i = 1; i <= count; i++) {
                Value value = new Value(prefixStr + Integer.toString(i));
                btree.addValue(value, i);
            }
            btree.flush();

            OutputStreamWriter writer = new OutputStreamWriter(System.out);
            btree.dump(writer);
            writer.flush();
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
            btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.open((short)-1);

            System.out.println("Rebuilding ...");
            btree.rebuild();

            OutputStreamWriter writer = new OutputStreamWriter(System.out);
            btree.dump(writer);
            writer.flush();
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
            btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
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
package org.exist.storage.btree;

import junit.framework.TestCase;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.UTF8;
import org.exist.util.ByteConversion;
import org.exist.util.XMLString;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.EXistException;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: 14.02.2007
 * Time: 20:00:28
 * To change this template use File | Settings | File Templates.
 */
public class BTreeTest extends TestCase {

    private BrokerPool pool;
    private File file = null;

    private int count = 0;
    private static final int COUNT = 10000;

    public void testStrings() {
        System.out.println("------------------ testStrings: START -------------------------");
        try {
            BTree btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.create((short) -1);

            int prefix = 99;
            String prefixStr = "Common prefix to all keys in this btree ";
            for (int i = 1; i <= COUNT; i++) {
                Value value = new PrefixValue(prefix, prefixStr + Integer.toString(i));
                btree.addValue(value, i);
            }

            btree.flush();
            System.out.println("BTree size: " + file.length());
            
            for (int i = 1; i <= COUNT; i++) {
                long p = btree.findValue(new PrefixValue(99, prefixStr + Integer.toString(i)));
                assertEquals(p, i);
            }

            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new PrefixValue(99));
            btree.query(query, new IndexCallback());
            assertEquals(count, COUNT);

            count = 0;
            query = new IndexQuery(IndexQuery.NEQ, new PrefixValue(99, prefixStr + "10"));
            btree.query(query, new IndexCallback());
            assertEquals(count, COUNT - 1);

            count = 0;
            query = new IndexQuery(IndexQuery.GT, new PrefixValue(99, "C"));
            btree.query(query, new IndexCallback());
            assertEquals(count, COUNT);

            count = 0;
            query = new IndexQuery(IndexQuery.LT, new PrefixValue(99, "C"));
            btree.query(query, new IndexCallback());
            assertEquals(count, 0);

            btree.close();
        } catch (DBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (TerminatedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        System.out.println("------------------ testStrings: END -------------------------");
    }

    public void testNumbers() {
        System.out.println("------------------ testNumbers: START -------------------------");
        try {
            BTree btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.create((short) -1);

            for (int i = 1; i <= COUNT; i++) {
                Value value = new PrefixValue(99, new DoubleValue(i));
                btree.addValue(value, i);
            }

            for (int i = 1; i <= COUNT; i++) {
                Value value = new PrefixValue(100, new DoubleValue(i));
                btree.addValue(value, i);
            }

            btree.flush();
            System.out.println("BTree size: " + file.length());

            for (int i = 1; i <= COUNT; i++) {
                long p = btree.findValue(new PrefixValue(99, new DoubleValue(i)));
                assertEquals(p, i);
            }
            Value prefix = new PrefixValue(99);

            count = 0;
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new PrefixValue(99));
            btree.query(query, new IndexCallback());
            assertEquals(count, COUNT);

            count = 0;
            query = new IndexQuery(IndexQuery.GT, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new IndexCallback());
            assertEquals(count, COUNT / 2);

            count = 0;
            query = new IndexQuery(IndexQuery.GEQ, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new IndexCallback());
            assertEquals(count, COUNT / 2 + 1);

            count = 0;
            query = new IndexQuery(IndexQuery.LT, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new IndexCallback());
            assertEquals(count, COUNT / 2 - 1);

            count = 0;
            query = new IndexQuery(IndexQuery.LEQ, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new IndexCallback());
            assertEquals(count, COUNT / 2);

            btree.close();
        } catch (DBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (TerminatedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (EXistException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        System.out.println("------------------ testNumbers: END -------------------------");
    }

    protected void setUp() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();

            file = new File(System.getProperty("exist.home", ".") + "/test/junit/test.dbx");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    protected void tearDown() {
    	try {
	        BrokerPool.stopAll(false);

            file.delete();
        } catch (Exception e) {
	        fail(e.getMessage());
	    }
    }

    private final class IndexCallback implements BTreeCallback {

        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            int prefix = ByteConversion.byteToInt(value.data(), value.start());
            assertEquals(prefix, 99);
            XMLString key = UTF8.decode(value.data(), value.start() + 4, value.getLength() - 4);
            System.out.println(prefix + " : " + key);
            count++;
            return false;
        }
    }

    private class PrefixValue extends Value {

        public PrefixValue(int prefix) {
            len = 4;
            data = new byte[len];
            ByteConversion.intToByte(prefix, data, 0);
            pos = 0;
        }
        
        public PrefixValue(int prefix, String key) {
            len = UTF8.encoded(key) + 4;
            data = new byte[len];
            ByteConversion.intToByte(prefix, data, 0);
            UTF8.encode(key, data, 4);
            pos = 0;
        }

        public PrefixValue(int prefix, AtomicValue value) throws EXistException {
            data = value.serializeValue(4);
            len = data.length;
            ByteConversion.intToByte(prefix, data, 0);
            pos = 0;
        }
    }
}

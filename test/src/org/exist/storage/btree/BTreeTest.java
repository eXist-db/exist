package org.exist.storage.btree;

import junit.framework.TestCase;
import org.exist.storage.BrokerPool;
import org.exist.util.*;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.EXistException;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Low-level tests on the B+tree.
 */
public class BTreeTest extends TestCase {

    private BrokerPool pool;
    private File file = null;

    private int count = 0;
    private static final int COUNT = 10000;

    public void testStrings() {
        System.out.println("------------------ testStrings: START -------------------------");
        BTree btree = null;
        try {
            btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.create((short) -1);

            String prefixStr = "C";
            for (int i = 1; i <= COUNT; i++) {
                Value value = new Value(prefixStr + Integer.toString(i));
                btree.addValue(value, i);
            }

            btree.flush();
            System.out.println("BTree size: " + file.length());

            StringWriter writer = new StringWriter();
            btree.dump(writer);
            System.out.println(writer.toString());
            
            for (int i = 1; i <= COUNT; i++) {
                long p = btree.findValue(new Value(prefixStr + Integer.toString(i)));
                assertEquals(p, i);
            }

            System.out.println("Testing IndexQuery.TRUNC_RIGHT");
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(prefixStr));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT, count);

            System.out.println("Testing IndexQuery.TRUNC_RIGHT");
            query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(prefixStr + "1"));
            btree.query(query, new StringIndexCallback());
            assertEquals(1112, count);

            System.out.println("Testing IndexQuery.NEQ");
            query = new IndexQuery(IndexQuery.NEQ, new Value(prefixStr + "10"));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT - 1, count);

            System.out.println("Testing IndexQuery.GT");
            query = new IndexQuery(IndexQuery.GT, new Value(prefixStr));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT, count);

            System.out.println("Testing IndexQuery.GT");
            query = new IndexQuery(IndexQuery.GT, new Value(prefixStr + "1"));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT - 1, count);

            System.out.println("Testing IndexQuery.LT");
            query = new IndexQuery(IndexQuery.LT, new Value(prefixStr));
            btree.query(query, new StringIndexCallback());
            assertEquals(count, 0);
        } catch (DBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (TerminatedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (btree != null)
                try {
                    btree.close();
                } catch (DBException e) {
                }
        }
        System.out.println("------------------ testStrings: END -------------------------");
    }

    public void testStringsTruncated() {
        System.out.println("------------------ testStringsTruncated: START -------------------------");
        BTree btree = null;
        try {
            btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.create((short) -1);

            char prefix = 'A';
            for (int i = 0; i < 24; i++) {
                for (int j = 1; j <= COUNT; j++) {
                    Value value = new Value(prefix + Integer.toString(j));
                    btree.addValue(value, j);
                }
                prefix++;
            }

            btree.flush();

            System.out.println("Testing IndexQuery.TRUNC_RIGHT");
            prefix = 'A';
            for (int i = 0; i < 24; i++) {
                IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(Character.toString(prefix)));
                btree.query(query, new StringIndexCallback());
                assertEquals(COUNT, count);
                prefix++;
            }
        } catch (DBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (TerminatedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (btree != null)
                try {
                    btree.close();
                } catch (DBException e) {
                }
        }
        System.out.println("------------------ testStringsTruncated: END -------------------------");
    }

    public void testRemoveStrings() {
        System.out.println("------------------ testRemoveStrings: START -------------------------");
        BTree btree = null;
        try {
            btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.create((short) -1);

            char prefix = 'A';
            for (int i = 0; i < 24; i++) {
                for (int j = 1; j <= COUNT; j++) {
                    Value value = new Value(prefix + Integer.toString(j));
                    btree.addValue(value, j);
                }
                prefix++;
            }
            btree.flush();

            prefix = 'A';
            for (int i = 0; i < 24; i++) {
                IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(Character.toString(prefix)));
                btree.remove(query, new StringIndexCallback());
                assertEquals(COUNT, count);

                assertEquals(-1, btree.findValue(new Value(prefix + Integer.toString(100))));

                query = new IndexQuery(IndexQuery.TRUNC_RIGHT,  new Value(prefix + Integer.toString(100)));
                btree.query(query, new StringIndexCallback());
                assertEquals(0, count);
                prefix++;
            }

            System.out.println("Testing IndexQuery.TRUNC_RIGHT");
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(Character.toString('D')));
            btree.query(query, new StringIndexCallback());
            assertEquals(0, count);
        } catch (DBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (TerminatedException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (btree != null)
                try {
                    btree.close();
                } catch (DBException e) {
                }
        }
        System.out.println("------------------ testRemoveStrings: END -------------------------");
    }

    public void testNumbers() throws TerminatedException {
        System.out.println("------------------ testNumbers: START -------------------------");
        try {
            BTree btree = new BTree(pool, (byte) 0, false, pool.getCacheManager(), file, 0.1);
            btree.create((short) -1);

            for (int i = 1; i <= COUNT; i++) {
                Value value = new SimpleValue(new DoubleValue(i));
                btree.addValue(value, i);
            }
            btree.flush();

            for (int i = 1; i <= COUNT; i++) {
                long p = btree.findValue(new SimpleValue(new DoubleValue(i)));
                assertEquals(p, i);
            }

            System.out.println("Testing IndexQuery.GT");
            IndexQuery query;
            for (int i = 0; i < COUNT; i += 10) {
                query = new IndexQuery(IndexQuery.GT, new SimpleValue(new DoubleValue(i)));
                btree.query(query, new SimpleCallback());
                assertEquals(COUNT - i, count);
            }

            System.out.println("Testing IndexQuery.GEQ");
            query = new IndexQuery(IndexQuery.GEQ, new SimpleValue(new DoubleValue(COUNT / 2)));
            btree.query(query, new SimpleCallback());
            assertEquals(COUNT / 2 + 1, count);

            System.out.println("Testing IndexQuery.NEQ");
            for (int i = 1; i <= COUNT / 8; i++) {
                query = new IndexQuery(IndexQuery.NEQ, new SimpleValue(new DoubleValue(i)));
                btree.query(query, new SimpleCallback());
                assertEquals(COUNT - 1, count);
            }

            btree.close();
        } catch (DBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        } catch (EXistException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        System.out.println("------------------ testNumbers: END -------------------------");
    }

    public void testNumbersWithPrefix() {
        System.out.println("------------------ testNumbersWithPrefix: START -------------------------");
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

            System.out.println("Testing IndexQuery.TRUNC_RIGHT");
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new PrefixValue(99));
            btree.query(query, new PrefixIndexCallback());
            assertEquals(COUNT, count);

            System.out.println("Testing IndexQuery.GT");
            for (int i = 0; i < COUNT; i += 10) {
                query = new IndexQuery(IndexQuery.GT, new PrefixValue(99, new DoubleValue(i)));
                btree.query(query, prefix, new PrefixIndexCallback());
                assertEquals(COUNT - i, count);
            }

            System.out.println("Testing IndexQuery.GEQ");
            query = new IndexQuery(IndexQuery.GEQ, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new PrefixIndexCallback());
            assertEquals(COUNT / 2 + 1, count);

            System.out.println("Testing IndexQuery.LT");
            query = new IndexQuery(IndexQuery.LT, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new PrefixIndexCallback());
            assertEquals(COUNT / 2 - 1, count);

            System.out.println("Testing IndexQuery.LEQ");
            query = new IndexQuery(IndexQuery.LEQ, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new PrefixIndexCallback());
            assertEquals(COUNT / 2, count);

            System.out.println("Testing IndexQuery.NEQ");
            for (int i = 1; i <= COUNT / 8; i++) {
                count = 0;
                query = new IndexQuery(IndexQuery.NEQ, new PrefixValue(99, new DoubleValue(i)));
                btree.query(query, prefix, new PrefixIndexCallback());
                assertEquals(COUNT - 1, count);
            }

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
        System.out.println("------------------ testNumbersWithPrefix: END -------------------------");
    }

    protected void setUp() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();

            file = new File(System.getProperty("exist.home", ".") + "/test/junit/test.dbx");
        } catch (Exception e) {
            e.printStackTrace();
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

    private final class SimpleCallback implements BTreeCallback {

        public SimpleCallback() {
            count = 0;
        }
        
        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            count++;
            return false;
        }
    }

    private final class PrefixIndexCallback implements BTreeCallback {

        public PrefixIndexCallback() {
            count = 0;
        }
        
        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            int prefix = ByteConversion.byteToInt(value.data(), value.start());
            assertEquals(99, prefix);
            XMLString key = UTF8.decode(value.data(), value.start() + 4, value.getLength() - 4);
//            System.out.println(prefix + " : " + key);
            count++;
            return false;
        }
    }

    private final class StringIndexCallback implements BTreeCallback {

        public StringIndexCallback() {
            count = 0;
        }
        
        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            XMLString key = UTF8.decode(value.data(), value.start(), value.getLength());
//            System.out.println("\"" + key + "\": " + count);
            count++;
            return false;
        }
    }

    private class SimpleValue extends Value {

        public SimpleValue(AtomicValue value) throws EXistException {
            data = value.serializeValue(0);
            len = data.length;
            pos = 0;
        }
    }

    private class PrefixValue extends Value {

        public PrefixValue(int prefix) {
            len = 4;
            data = new byte[len];
            ByteConversion.intToByte(prefix, data, 0);
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

package org.exist.storage.btree;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.*;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DoubleValue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Low-level tests on the B+tree.
 */
public class BTreeTest {

    private final static byte BTREE_TEST_FILE_ID = 0x7F;
    private final static short BTREE_TEST_FILE_VERSION = Short.MIN_VALUE;

    private Path file = null;

    private int count = 0;
    private static final int COUNT = 5000;

    @Test
    public void simpleUpdates() throws DBException, IOException, TerminatedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final BTree btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file)) {
            btree.create((short) -1);

            String prefixStr = "K";
            for (int i = 1; i <= COUNT; i++) {
                Value value = new Value(prefixStr + Integer.toString(i));
                btree.addValue(value, i);
            }

            //Testing IndexQuery.TRUNC_RIGHT
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(prefixStr));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT, count);
            btree.flush();

            //Removing index entries
            btree.remove(query, new StringIndexCallback());
            assertEquals(COUNT, count);
            btree.flush();

            //Reading data
            for (int i = 1; i <= COUNT; i++) {
                Value value = new Value(prefixStr + Integer.toString(i));
                btree.addValue(value, i);
            }

            //Testing IndexQuery.TRUNC_RIGHT
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT, count);
            btree.flush();
        }
    }

    @Test
    public void strings() throws DBException, IOException, TerminatedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final BTree btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file)) {
            btree.create((short) -1);

            String prefixStr = "C";
            for (int i = 1; i <= COUNT; i++) {
                Value value = new Value(prefixStr + Integer.toString(i));
                btree.addValue(value, i);
            }

            btree.flush();

            try(final StringWriter writer = new StringWriter()) {
                btree.dump(writer);
            }

            for (int i = 1; i <= COUNT; i++) {
                long p = btree.findValue(new Value(prefixStr + Integer.toString(i)));
                assertEquals(p, i);
            }

            //Testing IndexQuery.TRUNC_RIGHT
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(prefixStr));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT, count);

            //Testing IndexQuery.TRUNC_RIGHT
            query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(prefixStr + "1"));
            btree.query(query, new StringIndexCallback());
            assertEquals(1111, count);

            //Testing IndexQuery.NEQ
            query = new IndexQuery(IndexQuery.NEQ, new Value(prefixStr + "10"));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT - 1, count);

            //Testing IndexQuery.GT
            query = new IndexQuery(IndexQuery.GT, new Value(prefixStr));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT, count);

            //Testing IndexQuery.GT
            query = new IndexQuery(IndexQuery.GT, new Value(prefixStr + "1"));
            btree.query(query, new StringIndexCallback());
            assertEquals(COUNT - 1, count);

            //Testing IndexQuery.LT
            query = new IndexQuery(IndexQuery.LT, new Value(prefixStr));
            btree.query(query, new StringIndexCallback());
            assertEquals(count, 0);
        }
    }

    @Test
    public void longStrings() throws DBException, IOException {
        // Test storage of long keys up to half of the page size (4k)
        final Random rand = new Random(System.currentTimeMillis());

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final BTree btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file)) {
            btree.setSplitFactor(0.7);
            btree.create((short) -1);

            Map<String, Integer> keys = new TreeMap<>();
            String prefixStr = "C";
            for (int i = 1; i <= COUNT; i++) {
                StringBuilder buf = new StringBuilder();
                buf.append(prefixStr).append(Integer.toString(i));
                int nextLen = rand.nextInt(2000);
                while (nextLen < 512) {
                    nextLen = rand.nextInt(2000);
                }
                for (int j = 0; j < nextLen; j++) {
                    buf.append('x');
                }
                final String key = buf.toString();

                Value value = new Value(key);
                btree.addValue(value, i);
                keys.put(key, i);
            }

            btree.flush();

            for (Map.Entry<String, Integer> entry: keys.entrySet()) {
                long p = btree.findValue(new Value(entry.getKey().toString()));
                assertEquals(p, entry.getValue().intValue());
            }
        }
    }

    @Test
    public void stringsTruncated() throws DBException, IOException, TerminatedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(BTree btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file)) {
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

            //Testing IndexQuery.TRUNC_RIGHT
            prefix = 'A';
            for (int i = 0; i < 24; i++) {
                IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(Character.toString(prefix)));
                btree.query(query, new StringIndexCallback());
                assertEquals(COUNT, count);
                prefix++;
            }
        }
    }

    @Test
    public void removeStrings() throws DBException, IOException, TerminatedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final BTree btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file)) {
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

            //Testing IndexQuery.TRUNC_RIGHT
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new Value(Character.toString('D')));
            btree.query(query, new StringIndexCallback());
            assertEquals(0, count);
        }
    }

    @Test
    public void numbers() throws TerminatedException, DBException, EXistException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final BTree btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file)) {
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

            //Testing IndexQuery.GT
            IndexQuery query;
            for (int i = 0; i < COUNT; i += 10) {
                query = new IndexQuery(IndexQuery.GT, new SimpleValue(new DoubleValue(i)));
                btree.query(query, new SimpleCallback());
                assertEquals(COUNT - i, count);
            }

            //Testing IndexQuery.GEQ
            query = new IndexQuery(IndexQuery.GEQ, new SimpleValue(new DoubleValue(COUNT / 2)));
            btree.query(query, new SimpleCallback());
            assertEquals(COUNT / 2 + 1, count);

            //Testing IndexQuery.NEQ
            for (int i = 1; i <= COUNT / 8; i++) {
                query = new IndexQuery(IndexQuery.NEQ, new SimpleValue(new DoubleValue(i)));
                btree.query(query, new SimpleCallback());
                assertEquals(COUNT - 1, count);
            }
        }
    }

    @Test
    public void numbersWithPrefix() throws DBException, EXistException, IOException, TerminatedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final BTree btree = new BTree(pool, BTREE_TEST_FILE_ID, BTREE_TEST_FILE_VERSION, false, pool.getCacheManager(), file)) {
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

            for (int i = 1; i <= COUNT; i++) {
                long p = btree.findValue(new PrefixValue(99, new DoubleValue(i)));
                assertEquals(p, i);
            }
            Value prefix = new PrefixValue(99);

            //Testing IndexQuery.TRUNC_RIGHT
            IndexQuery query = new IndexQuery(IndexQuery.TRUNC_RIGHT, new PrefixValue(99));
            btree.query(query, new PrefixIndexCallback());
            assertEquals(COUNT, count);

            //Testing IndexQuery.GT
            for (int i = 0; i < COUNT; i += 10) {
                query = new IndexQuery(IndexQuery.GT, new PrefixValue(99, new DoubleValue(i)));
                btree.query(query, prefix, new PrefixIndexCallback());
                assertEquals(COUNT - i, count);
            }

            //Testing IndexQuery.GEQ
            query = new IndexQuery(IndexQuery.GEQ, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new PrefixIndexCallback());
            assertEquals(COUNT / 2 + 1, count);

            //Testing IndexQuery.LT
            query = new IndexQuery(IndexQuery.LT, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new PrefixIndexCallback());
            assertEquals(COUNT / 2 - 1, count);

            //Testing IndexQuery.LEQ
            query = new IndexQuery(IndexQuery.LEQ, new PrefixValue(99, new DoubleValue(COUNT / 2)));
            btree.query(query, prefix, new PrefixIndexCallback());
            assertEquals(COUNT / 2, count);

            //Testing IndexQuery.NEQ
            for (int i = 1; i <= COUNT / 8; i++) {
                count = 0;
                query = new IndexQuery(IndexQuery.NEQ, new PrefixValue(99, new DoubleValue(i)));
                btree.query(query, prefix, new PrefixIndexCallback());
                assertEquals(COUNT - 1, count);
            }
        }
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void initialize() throws IOException {
        file = temporaryFolder.newFile("test.dbx").toPath();
        assertTrue(Files.exists(file));
    }

    @After
    public void cleanUp() {
        FileUtils.deleteQuietly(file);
    }

    private final class SimpleCallback implements BTreeCallback {

        public SimpleCallback() {
            count = 0;
        }

        @Override
        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            count++;
            return false;
        }
    }

    private final class PrefixIndexCallback implements BTreeCallback {

        public PrefixIndexCallback() {
            count = 0;
        }

        @Override
        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            int prefix = ByteConversion.byteToInt(value.data(), value.start());
            assertEquals(99, prefix);
//            XMLString key = UTF8.decode(value.data(), value.start() + 4, value.getLength() - 4);
            count++;
            return false;
        }
    }

    private final class StringIndexCallback implements BTreeCallback {

        public StringIndexCallback() {
            count = 0;
        }

        @Override
        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
//            @SuppressWarnings("unused")
//			XMLString key = UTF8.decode(value.data(), value.start(), value.getLength());
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

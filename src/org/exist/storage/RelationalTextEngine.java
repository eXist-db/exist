/*
 *  eXist  Open  Source Native XML Database
 *
 *  Copyright  (C) 2000, Wolfgang Meier (meier@ifs. tu- darmstadt. de)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.storage;

import org.apache.log4j.Category;
import org.exist.dom.*;
import org.exist.storage.analysis.TextToken;
import org.exist.util.*;
import org.w3c.dom.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.sql.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * This class is responsible for the fulltext-indexing. Text-nodes are handed
 * over to this class to be fulltext-indexed. Method storeText() is called by
 * RelationalBroker whenever it finds a TextNode. Method
 * getNodeIDsContaining() is used by the XPath-engine to process queries where
 * a fulltext-operator is involved. The class keeps two database tables: table
 * words stores the words found with their unique id. Table inv_idx contains
 * the word occurrences for every word-id per document.
 *
 * @author wolf
 *
 */
public class RelationalTextEngine extends TextSearchEngine {
    private static Category LOG =
        Category.getInstance( RelationalTextEngine.class.getName(  ) );
    protected static RelationalBroker.TableLock wordsLock =
        new RelationalBroker.TableLock(  );
    protected static Map words = Collections.synchronizedMap( new HashMap(  ) );
    protected static int lastWordId = -1;

    protected DBConnectionPool pool;

    protected InvertedIndex t_insert;

    protected PreparedStatement m_insertWordStmt;

    protected PreparedStatement m_getWordIdStmt;

    protected PreparedStatement m_getNextWordId;

    protected PreparedStatement m_getStmt;

    protected Statement stmt;

    protected boolean useCompression = false;

    protected boolean indexNumbers = false;

    protected String enc;

    protected String tmpDir;

    protected String WORDS_FILE = "words";

    protected RelationalBroker.TableOutputStream s_words;

    /**
     * Constructor for the RelationalTextEngine object
     *
     * @param broker Description of the Parameter
     * @param config Description of the Parameter
     * @param pool Description of the Parameter
     */
    public RelationalTextEngine( DBBroker broker, Configuration config,
                                 DBConnectionPool pool
                                 ) {
        super( broker, config );
        this.pool = pool;

        Boolean num;
        String pathSep = System.getProperty( "file.separator", "/" );

        if ( ( tmpDir = (String) config.getProperty( "tmpDir" ) ) == null )
            tmpDir = null;

        if ( ( enc = (String) config.getProperty( "encoding" ) ) == null )
            enc = "UTF8";

        t_insert = new InvertedIndex( 0, pool, useCompression );

        Connection con = pool.get(  );

        try {
            stmt = con.createStatement(  );
            m_getNextWordId =
                con.prepareStatement( "select max(word_id) from words" );
            m_getWordIdStmt =
                con.prepareStatement( "select word_id from words where wdata=?" );
            m_insertWordStmt =
                con.prepareStatement( "insert into words (word_id, wdata) values (?, ?)" );
            pool.release( con );
        } catch ( SQLException e ) {
            pool.release( con );
            LOG.debug( e );
        }
    }

    /**
     * Index  a text node
     *
     * @param idx IndexPaths object passed in by the broker
     * @param text the text node to be indexed
     */
    public void storeText( IndexPaths idx, TextImpl text ) {
        DocumentImpl doc = (DocumentImpl) text.getOwnerDocument(  );
        Integer docId = new Integer( doc.getDocId(  ) );
        Long gid = new Long( text.getGID(  ) );
        tokenizer.setText( text.getData(  ) );

        String word;
        TextToken token;
        ResultSet rid;
        int word_id = 0;

        if ( broker.getDatabaseType(  ) != RelationalBroker.MYSQL )
            try {
                m_insertWordStmt.getConnection(  ).setAutoCommit( false );
            } catch ( SQLException sqe ) {
                LOG.debug( sqe );
            }

        while ( null != ( token = tokenizer.nextToken(  ) ) ) {
            if ( idx != null && idx.getIncludeAlphaNum(  ) == false
                     && ( token.getType(  ) == TextToken.ALPHANUM )
                 )
                continue;

            word = token.getText(  );

            if ( stoplist.contains( word ) )
                continue;

            if ( words.containsKey( word ) )
                word_id = ( (Integer) words.get( word ) ).intValue(  );
            else {
                Object lock = wordsLock.acquire(  );

                try {
                    m_getWordIdStmt.setString( 1, word );
                    rid = m_getWordIdStmt.executeQuery(  );

                    if ( !rid.next(  ) ) {
                        word_id = getNextWordId(  );
                        m_insertWordStmt.setInt( 1, word_id );
                        m_insertWordStmt.setString( 2, word );
                        m_insertWordStmt.executeUpdate(  );
                    } else
                        word_id = rid.getInt( 1 );

                    rid.close(  );

                    /*
                     *  if(broker.getDatabaseType() != RelationalBroker.MYSQL)
                     *  m_insertWordStmt.getConnection().setAutoCommit(false);
                     */
                    words.put( word, new Integer( word_id ) );
                } catch ( SQLException e ) {
                    LOG.warn( "sql exception while storing cdata: " + e );
                } finally {
                    wordsLock.release( lock );
                }
            }

            t_insert.setDocId( doc.getDocId(  ) );
            t_insert.addRow( word_id, text.getGID(  ) );
        }
    }

    /**
     * Gets the nextWordId attribute of the RelationalTextEngine object
     *
     * @return The nextWordId value
     */
    private int getNextWordId(  ) {
        if ( lastWordId > -1 ) {
            ++lastWordId;

            return lastWordId;
        }

        try {
            ResultSet r = m_getNextWordId.executeQuery(  );

            if ( !r.next(  ) )
                return 0;

            int wordId = r.getInt( 1 ) + 1;
            lastWordId = wordId;

            return wordId;
        } catch ( SQLException e ) {
            LOG.debug( e );

            return -1;
        }
    }

    /**
     * Description of the Method
     *
     * @param docs Description of the Parameter
     *
     * @return Description of the Return Value
     */
    private static String documentSet( DocumentSet docs ) {
        StringBuffer r = new StringBuffer(  );
        r.append( '(' );

        for ( int i = 0; i < docs.getLength(  ); i++ ) {
            if ( r.length(  ) > 1 )
                r.append( ',' );

            int docId = ( (DocumentImpl) docs.item( i ) ).getDocId(  );
            r.append( docId );
        }

        r.append( ')' );

        return r.toString(  );
    }

	/**
		 *  Find    all the nodes containing the search terms given by the array
		 * expr from the fulltext-index.
		 *
		 * @param the input document set
		 * @param array of regular expression search terms
		 * @return array containing a NodeSet for each of the search terms
		 *
		 */
    public NodeSet[] getNodesContaining( DocumentSet docs, String[] expr ) {
        NodeSet[] result = null;

        //result.setIsSorted(true);
        long start = System.currentTimeMillis(  );
        ArrayList wordId = new ArrayList( 10 );
        Connection con = pool.get(  );

        try {
            //Statement stmt = con.createStatement();
            StringBuffer sql =
                new StringBuffer( "select word_id from words where " );

            for ( int i = 0; i < expr.length; i++ ) {
                if ( i > 0 )
                    sql.append( " or " );

                sql.append( "wdata like '" );

                if ( stem )
                    sql.append( stemmer.stem( expr[i] ) );
                else
                    sql.append( expr[i] );

                sql.append( '\'' );
            }

            LOG.debug( sql.toString(  ) );

            ResultSet rs = stmt.executeQuery( sql.toString(  ) );
            Integer current;

            while ( rs.next(  ) )
                wordId.add( new Integer( rs.getInt( 1 ) ) );

            rs.close(  );
        } catch ( SQLException e ) {
            LOG.warn( e );
            pool.release( con );

            return null;
        } catch ( Exception ex ) {
            LOG.warn( ex );
            pool.release( con );
        }

        if ( wordId.size(  ) == 0 ) {
            result = new NodeSet[1];
            result[0] = new ArraySet( 0 );
            pool.release( con );

            return result;
        }

        try {
            StringBuffer sql = new StringBuffer(  );
            sql.append( "select word_id, doc_id, data "
                        + " from inv_idx where doc_id in "
                        );
            sql.append( documentSet( docs ) );
            sql.append( "and " );

            for ( int i = 0; i < wordId.size(  ); i++ ) {
                if ( i > 0 )
                    sql.append( " or " );

                sql.append( "word_id=" );
                sql.append( wordId.get( i ) );
            }

            sql.append( " order by word_id" );
            LOG.debug( sql.toString(  ) );

            ResultSet r = stmt.executeQuery( sql.toString(  ) );
            int id;
            int last_word_id = -1;
            int count = -1;
            short doc_id;
            long gid;
            result = new NodeSet[wordId.size(  )];

            NodeSet resultSet = null;
            byte[] data;
            byte[] temp = new byte[8];

            while ( r.next(  ) ) {
                id = r.getInt( 1 );
                doc_id = r.getShort( 2 );
                data = r.getBytes( 3 );

                if ( id > last_word_id ) {
                    result[++count] = new ArraySet( 100 );
                    last_word_id = id;
                }

                for ( int i = 0; i < data.length; i += 8 ) {
                    gid = ByteConversion.byteToLong( data, i );
                    result[count].add( new NodeProxy( docs.getDoc( doc_id ),
                                                      gid, Node.TEXT_NODE
                                                      )
                                       );
                }
            }

            r.close(  );
            LOG.debug( "getNodeIdsContaining took "
                       + ( System.currentTimeMillis(  ) - start )
                       );
        } catch ( SQLException s ) {
            LOG.warn( s );
        }

        pool.release( con );

        return result;
    }

    /**
     * Description of the Method
     */
    public void flush(  ) {
        try {
            t_insert.flush(  );

            if ( broker.getDatabaseType(  ) != RelationalBroker.MYSQL )
                m_insertWordStmt.getConnection(  ).commit(  );
        } catch ( SQLException e ) {
            LOG.warn( e );
        }
    }

    /**
     * Description of the Method
     */
    public void close(  ) {
        try {
            flush(  );
            stmt.close(  );
            m_getWordIdStmt.close(  );
            m_insertWordStmt.close(  );
        } catch ( SQLException e ) {
            LOG.debug( e );
        }
    }

    /**
     * This inner class is responsible for actually storing the list of
     * occurrences.
     *
     * @author wolf
     *
     * @deprecated April 2, 2002
     */
    class InvertedIndex {
        /** Description of the Field */
        protected DBConnectionPool pool;

        /** Description of the Field */
        protected PreparedStatement m_insert;

        /** Description of the Field */
        protected PreparedStatement m_get;

        /** Description of the Field */
        protected PreparedStatement m_update;

        /** Description of the Field */
        protected Statement stmt;

        /** Description of the Field */
        protected HashMap wordIds = new HashMap(  );

        /** Description of the Field */
        protected int doc_id;

        /** Description of the Field */
        protected boolean compress = false;

        /** Description of the Field */
        protected boolean flushed = false;
        private final int MAX_BUF = 100;

        /**
         * Constructor for the InvertedIndex object
         *
         * @param doc_id Description of the Parameter
         * @param pool Description of the Parameter
         * @param compress Description of the Parameter
         */
        public InvertedIndex( int doc_id, DBConnectionPool pool,
                              boolean compress
                              ) {
            this.pool = pool;
            this.doc_id = doc_id;
            this.compress = compress;

            Connection con = pool.get(  );

            try {
                m_insert =
                    con.prepareStatement( "insert into inv_idx (doc_id, word_id, "
                                          + "data) values (?, ?, ?)"
                                          );
                m_get =
                    con.prepareStatement( "select word_id, data from inv_idx where "
                                          + "doc_id=? and word_id=?"
                                          );
                m_update =
                    con.prepareStatement( "update inv_idx set data=? "
                                          + "where doc_id=? and word_id=?"
                                          );
                stmt = con.createStatement(  );
            } catch ( SQLException e ) {
                LOG.debug( e );
            }

            pool.release( con );
        }

        /**
         * Sets the docId attribute of the InvertedIndex object
         *
         * @param docId The new docId value
         */
        public void setDocId( int docId ) {
            if ( this.doc_id != docId )
                flush(  );

            this.doc_id = docId;
        }

        /**
         * Adds a feature to the Row attribute of the InvertedIndex object
         *
         * @param word_id The feature to be added to the Row attribute
         * @param gid The feature to be added to the Row attribute
         */
        public void addRow( int word_id, long gid ) {
            Integer id = new Integer( word_id );
            ByteArrayOutputStream b_buf;

            if ( wordIds.containsKey( id ) )
                b_buf = (ByteArrayOutputStream) wordIds.get( id );
            else {
                b_buf = new ByteArrayOutputStream(  );
                wordIds.put( id, b_buf );
            }

            try {
                byte[] temp = new byte[8];
                ByteConversion.longToByte( gid, temp, 0 );
                b_buf.write( temp );
            } catch ( IOException e ) {
                LOG.warn( e );
            }

            Runtime run = Runtime.getRuntime(  );

            if ( run.freeMemory(  ) < 500000 ) {
                flush(  );
                System.gc(  );
                flushed = true;
            }
        }

        /**
         * Description of the Method
         */
        protected void lockTables(  ) {
            try {
                stmt.executeUpdate( "lock tables inv_idx write, words write" );
            } catch ( SQLException e ) {
                LOG.warn( "could not lock tables: " + e );
            }
        }

        /**
         * Description of the Method
         */
        protected void unlockTables(  ) {
            try {
                stmt.executeUpdate( "unlock tables" );
            } catch ( SQLException e ) {
                LOG.warn( "could not lock tables: " + e );
            }
        }

        /**
         * Description of the Method
         */
        public void flush(  ) {
            if ( wordIds.size(  ) == 0 )
                return;

            if ( broker.getDatabaseType(  ) == RelationalBroker.MYSQL )
                lockTables(  );
            else

                try {
                    m_insert.getConnection(  ).setAutoCommit( false );
                } catch ( SQLException sqe ) {
                    LOG.debug( sqe );
                }

            ProgressIndicator progress =
                new ProgressIndicator( wordIds.size(  ) );
            int count = 1;

            for ( Iterator i = wordIds.keySet(  ).iterator(  ); i.hasNext(  );
                      count++
                  ) {
                Integer id = (Integer) i.next(  );
                ByteArrayOutputStream b_buf =
                    (ByteArrayOutputStream) wordIds.get( id );
                byte[] data = b_buf.toByteArray(  );

                if ( data.length == 0 )
                    return;

                flushWord( id.intValue(  ), data );

                progress.setValue( count );
                setChanged(  );
                notifyObservers( progress );
            }

            try {
                if ( broker.getDatabaseType(  ) != RelationalBroker.MYSQL )
                    m_insert.getConnection(  ).commit(  );
            } catch ( SQLException sqe ) {
                LOG.warn( sqe );
            }

            wordIds = new HashMap(  );

            if ( broker.getDatabaseType(  ) == RelationalBroker.MYSQL )
                unlockTables(  );
        }

        /**
         * Description of the Method
         *
         * @param wordId Description of the Parameter
         * @param data Description of the Parameter
         */
        private void flushWord( int wordId, byte[] data ) {
            if ( data.length == 0 )
                return;

            if ( data.length > 16777215 ) {
                LOG.warn( "word index longer than max blob size." );

                return;
            }

            // if data has already been written to the table,
            // we may need to do updates.
            if ( flushed ) {
                try {
                    m_get.setInt( 1, doc_id );
                    m_get.setInt( 2, wordId );

                    ResultSet rs = m_get.executeQuery(  );

                    if ( rs.next(  ) ) {
                        byte[] oldData;
                        String dstr;
                        oldData = rs.getBytes( 2 );

                        byte[] newData = new byte[data.length + oldData.length];
                        System.arraycopy( oldData, 0, newData, 0, oldData.length );
                        System.arraycopy( data, 0, newData, oldData.length,
                                          data.length
                                          );
                        m_update.setBytes( 1, newData );
                        m_update.setInt( 2, doc_id );
                        m_update.setInt( 3, wordId );
                        m_update.executeUpdate(  );

                        /*
                         *  if(broker.getDatabaseType() != RelationalBroker.MYSQL)
                         *  m_update.getConnection().commit();
                         */
                        return;
                    }
                } catch ( SQLException e ) {
                    LOG.debug( e );
                }
            }

            if ( ( data == null ) || ( data.length == 0 ) ) {
                LOG.debug( "no data" );

                return;
            }

            // insert a new row
            try {
                m_insert.setInt( 1, doc_id );
                m_insert.setInt( 2, wordId );
                m_insert.setBytes( 3, data );
                m_insert.executeUpdate(  );

                /*
                 *  if(broker.getDatabaseType() != RelationalBroker.MYSQL)
                 *  m_insert.getConnection().commit();
                 */
            } catch ( SQLException e ) {
                LOG.warn( e );
                e.printStackTrace(  );

                for ( int i = 0; i < data.length; i++ )
                    System.out.println( data[i] );

                System.exit( 0 );
            }
        }
    }
}

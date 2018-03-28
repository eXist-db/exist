/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.Index;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.storage.btree.BTree;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Occurrences;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XQueryContext;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class IndexManagerTest {

  @ClassRule
  public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

  private static final String INDEX_ID = "test";

  @Test
  public void configurationChangeRuntime() throws Exception  {

    TestIndex index = new TestIndex();

    final BrokerPool pool = existEmbeddedServer.getBrokerPool();
    try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
      pool.getIndexManager().registerIndex(index);

      Assert.assertNull(broker.getIndexController().getWorkerByIndexId(INDEX_ID));
    }

    try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
      Assert.assertNotNull(broker.getIndexController().getWorkerByIndexId(INDEX_ID));
    }

  }

  class TestIndex implements Index {

    BrokerPool pool;

    @Override
    public String getIndexId() {
      return INDEX_ID;
    }

    @Override
    public String getIndexName() {
      return INDEX_ID;
    }

    @Override
    public BrokerPool getBrokerPool() {
      return pool;
    }

    @Override
    public void configure(BrokerPool pool, Path dataDir, Element config) {
      this.pool = pool;

    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public void sync() {
    }

    @Override
    public void remove() {
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
      return new IndexWorker() {

        @Override
        public String getIndexId() {
          return INDEX_ID;
        }

        @Override
        public String getIndexName() {
          return INDEX_ID;
        }

        @Override
        public Object configure(IndexController controller, NodeList configNodes,
            Map<String, String> namespaces) {
          return null;
        }

        @Override
        public void setDocument(DocumentImpl doc) {

        }

        @Override
        public void setDocument(DocumentImpl doc, ReindexMode mode) {

        }

        @Override
        public void setMode(ReindexMode mode) {

        }

        @Override
        public DocumentImpl getDocument() {
          return null;
        }

        @Override
        public ReindexMode getMode() {
          return null;
        }

        @Override
        public <T extends IStoredNode> IStoredNode getReindexRoot(IStoredNode<T> node,
            NodePath path,
            boolean insert, boolean includeSelf) {
          return null;
        }

        @Override
        public StreamListener getListener() {
          return null;
        }

        @Override
        public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
          return null;
        }

        @Override
        public void flush() {

        }

        @Override
        public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
        }

        @Override
        public boolean checkIndex(DBBroker broker) {
          return false;
        }

        @Override
        public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet,
            Map<?, ?> hints) {
          return new Occurrences[0];
        }

        @Override
        public QueryRewriter getQueryRewriter(XQueryContext context) {
          return null;
        }
      };
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
      return false;
    }

    @Override
    public BTree getStorage() {
      return null;
    }
  }

}

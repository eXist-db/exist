package org.exist.indexing.range;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.exist.dom.QName;
import org.exist.storage.NodePath;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class RangeIndexConfig {

    protected static final Logger LOG = Logger.getLogger(RangeIndexConfig.class);

    protected Map<QName, RangeIndexConfigElement> paths = new TreeMap<QName, RangeIndexConfigElement>();

    protected Analyzer analyzer;

    private PathIterator iterator = new PathIterator();

    public RangeIndexConfig() {
    }

    public RangeIndexConfig(RangeIndexConfig other) {
        this.paths = other.paths;
        this.analyzer = other.analyzer;
    }

    public RangeIndexConfigElement find(NodePath path) {
        for (RangeIndexConfigElement rice : paths.values()) {
            if (rice.find(path)) {
                return rice;
            }
        }
        return null;
    }

    public Analyzer getDefaultAnalyzer() {
        return analyzer;
    }

    public Analyzer getAnalyzer(QName qname, String fieldName) {
        Analyzer analyzer = null;
        if (qname != null) {
            RangeIndexConfigElement idxConf = paths.get(qname);
            if (idxConf != null) {
                analyzer = idxConf.getAnalyzer(null);
            }
        } else {
            for (RangeIndexConfigElement idxConf: paths.values()) {
                if (idxConf.isComplex()) {
                    analyzer = idxConf.getAnalyzer(fieldName);
                    if (analyzer != null) {
                        break;
                    }
                }
            }
        }
        return analyzer;
    }

    public boolean isCaseSensitive(QName qname, String fieldName) {
        boolean caseSensitive = true;
        if (qname != null) {
            RangeIndexConfigElement idxConf = paths.get(qname);
            if (idxConf != null) {
                caseSensitive = idxConf.isCaseSensitive(fieldName);
            }
        } else {
            for (RangeIndexConfigElement idxConf: paths.values()) {
                if (idxConf.isComplex()) {
                    caseSensitive = idxConf.isCaseSensitive(fieldName);
                    if (!caseSensitive) {
                        break;
                    }
                }
            }
        }
        return caseSensitive;
    }

    public Iterator<RangeIndexConfigElement> getConfig(NodePath path) {
        iterator.reset(path);
        return iterator;
    }

    public boolean matches(NodePath path) {
        RangeIndexConfigElement idxConf = paths.get(path.getLastComponent());
        while (idxConf != null) {
            if (idxConf.match(path))
                return true;
            idxConf = idxConf.getNext();
        }
        return false;
    }

    private class PathIterator implements Iterator<RangeIndexConfigElement> {

        private RangeIndexConfigElement nextConfig;
//        private NodePath path;
        private boolean atLast = false;

        protected void reset(NodePath path) {
            this.atLast = false;
//            this.path = path;
            nextConfig = paths.get(path.getLastComponent());
            if (nextConfig == null) {
                atLast = true;
            }
        }

        @Override
        public boolean hasNext() {
            return (nextConfig != null);
        }

        @Override
        public RangeIndexConfigElement next() {
            if (nextConfig == null)
                return null;

            RangeIndexConfigElement currentConfig = nextConfig;
            nextConfig = nextConfig.getNext();
            if (nextConfig == null && !atLast) {
                atLast = true;
            }
            return currentConfig;
        }

        @Override
        public void remove() {
            //Nothing to do
        }
    }
}
package org.exist.indexing.range;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.storage.DBBroker;

public class RangeIndex extends LuceneIndex {

    private static final Logger LOG = Logger.getLogger(RangeIndex.class);

    public final static String ID = RangeIndex.class.getName();

    public enum Operator {
        GT,
        LT,
        EQ,
        GE,
        LE,
        ENDS_WITH,
        STARTS_WITH,
        CONTAINS
    };

    private static final String DIR_NAME = "range";

    private Analyzer defaultAnalyzer = new KeywordAnalyzer();

    @Override
    public String getDirName() {
        return DIR_NAME;
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        return new RangeIndexWorker(this, broker);
    }

    @Override
    public String getIndexId() {
        return ID;
    }

    public Analyzer getDefaultAnalyzer() {
        return defaultAnalyzer;
    }
}

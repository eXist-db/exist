package org.exist.indexing.range.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.exist.dom.QName;
import org.exist.indexing.range.TextCollector;
import org.exist.storage.NodePath;
import org.w3c.dom.Node;

import java.io.IOException;

/**
 * Created by aretter on 04/05/2015.
 */
public interface RangeIndexConfigElement {

    QName getQName();

    void add(RangeIndexConfigElement config);

    RangeIndexConfigElement getNext();

    Analyzer getAnalyzer();

    Analyzer getAnalyzer(String field);

    boolean isCaseSensitive(String fieldName);

    int getType();

    int getType(String fieldName);

    boolean find(NodePath other);

    boolean match(NodePath otherPath);

    boolean match(NodePath otherPath, final Node other);

    TextCollector getCollector(NodePath path);

    Field convertToField(String fieldName, String content) throws IOException;
}

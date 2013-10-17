package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.exist.xquery.XPathException;

/**
 * Wrapper around Lucene's new flexible {@link StandardQueryParser}.
 *
 * @author Wolfgang
 */
public class StandardQueryParserWrapper extends QueryParserWrapper {

    private String field = null;
    private StandardQueryParser parser = null;

    public StandardQueryParserWrapper(String field, Analyzer analyzer) {
        super(field, analyzer);
        this.field = field;
        this.parser = new StandardQueryParser(analyzer);
    }

    @Override
    public CommonQueryParserConfiguration getConfiguration() {
        return parser;
    }

    @Override
    public Query parse(String query) throws XPathException {
        try {
            return parser.parse(query, field);
        } catch (QueryNodeException e) {
            throw new XPathException("Syntax error in Lucene query string: " + e.getMessage());
        }
    }
}

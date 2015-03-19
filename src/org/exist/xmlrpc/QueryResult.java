package org.exist.xmlrpc;

import java.io.IOException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;

import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.value.BinaryValue;

/**
 * Simple container for the results of a query. Used to cache
 * query results that may be retrieved later by the client.
 * 
 * @author wolf
 * @author jmfernandez
 */
public class QueryResult extends AbstractCachedResult {

    protected final static Logger LOG = LogManager.getLogger(QueryResult.class);
    protected Sequence result;
    protected Properties serialization = null;
    // set upon failure
    protected XPathException exception = null;

    public QueryResult(Sequence result, Properties outputProperties) {
        this(result, outputProperties, 0);
    }

    public QueryResult(Sequence result, Properties outputProperties, long queryTime) {
        super(queryTime);
        this.serialization = outputProperties;
        this.result = result;
    }

    public QueryResult(XPathException e) {
        exception = e;
    }

    public boolean hasErrors() {
        return exception != null;
    }

    public XPathException getException() {
        return exception;
    }

    /**
     * @return Returns the result.
     */
    @Override
    public Sequence getResult() {
        return result;
    }

    @Override
    public void free() {
        if(result != null) {

            //cleanup any binary values
            if(result instanceof BinaryValue) {
                try {
                    ((BinaryValue) result).close();
                } catch(final IOException ioe) {
                    LOG.warn("Unable to cleanup BinaryValue: " + result.hashCode(), ioe);
                }
            }

            result = null;
        }
    }
}

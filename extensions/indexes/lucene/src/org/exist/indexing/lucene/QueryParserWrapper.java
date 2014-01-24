package org.exist.indexing.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.Query;
import org.exist.xquery.XPathException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Wrapper around Lucene's parser classes, which unfortunately do not all inherit
 * from the same base class.
 *
 * @author Wolfgang
 */
public abstract class QueryParserWrapper {

    private final static Logger LOG = Logger.getLogger(QueryParserWrapper.class);

    public QueryParserWrapper(String field, Analyzer analyzer) {
    }

    public abstract CommonQueryParserConfiguration getConfiguration();

    public abstract Query parse(String query) throws XPathException;

    public static QueryParserWrapper create(String className, String field, Analyzer analyzer) {
        try {
            Class<?> clazz = Class.forName(className);
            if (QueryParserWrapper.class.isAssignableFrom(clazz)) {
                final Class<?> cParamClasses[] = new Class<?>[] {
                    String.class, Analyzer.class
                };
                final Constructor<?> cstr = clazz.getDeclaredConstructor(cParamClasses);
                return (QueryParserWrapper) cstr.newInstance(field, analyzer);
            }
        } catch (ClassNotFoundException e) {
            LOG.warn("Failed to instantiate lucene query parser wrapper class: " + className, e);
        } catch (NoSuchMethodException e) {
            LOG.warn("Failed to instantiate lucene query parser wrapper class: " + className, e);
        } catch (InstantiationException e) {
            LOG.warn("Failed to instantiate lucene query parser wrapper class: " + className, e);
        } catch (IllegalAccessException e) {
            LOG.warn("Failed to instantiate lucene query parser wrapper class: " + className, e);
        } catch (InvocationTargetException e) {
            LOG.warn("Failed to instantiate lucene query parser wrapper class: " + className, e);
        }
        return null;
    }
}
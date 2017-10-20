package org.exist.indexing.range;

import com.ibm.icu.text.Collator;
import org.apache.logging.log4j.LogManager;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.collation.ICUCollationAttributeFactory;
import org.apache.lucene.util.AttributeFactory;
import org.exist.util.Collations;
import org.exist.util.DatabaseConfigurationException;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.XPathException;
import org.w3c.dom.Element;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lucene analyzer used by the range index. Based on {@link KeywordTokenizer}, it allows additional
 * filters to be added to the pipeline through the collection.xconf configuration. A collation may be
 * specified as well.
 */
public class RangeIndexAnalyzer extends Analyzer {

    private final static Logger LOG = LogManager.getLogger(RangeIndexAnalyzer.class);

    private static class FilterConfig {
        Constructor<?> constructor;

        FilterConfig(Element config) throws DatabaseConfigurationException {
            final String className = config.getAttribute("class");
            if (className == null) {
                throw new DatabaseConfigurationException("No class specified for filter");
            }
            try {
                Class clazz = Class.forName(className);
                if (!TokenFilter.class.isAssignableFrom(clazz)) {
                    throw new DatabaseConfigurationException("Filter " + className + " is not a subclass of " +
                        TokenFilter.class.getName());
                }
                constructor = clazz.getConstructor(TokenStream.class);
            } catch (ClassNotFoundException e) {
                throw new DatabaseConfigurationException("Filter not found: " + className, e);
            } catch (NoSuchMethodException e) {
                throw new DatabaseConfigurationException("Filter class " + className + " has non-default " +
                        "constructor", e);
            }
        }
    }

    private List<FilterConfig> filterConfigs = new ArrayList<>();
    private Collator collator = null;

    public RangeIndexAnalyzer() {
    }

    public void addFilter(Element filter) throws DatabaseConfigurationException {
        filterConfigs.add(new FilterConfig(filter));
    }

    public void addCollation(String uri) throws DatabaseConfigurationException {
        try {
            collator = Collations.getCollationFromURI(uri);
        } catch (XPathException e) {
            throw new DatabaseConfigurationException(e.getMessage(), e);
        }
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
        AttributeFactory factory = AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY;
        if (collator != null) {
            factory = new ICUCollationAttributeFactory(collator);
        }
        Tokenizer src = new KeywordTokenizer(factory, reader, 256);
        TokenStream tok = src;
        for (FilterConfig filter: filterConfigs) {
            try {
                tok = (TokenStream) filter.constructor.newInstance(tok);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                LOG.warn(e.getMessage(), e);
            }
        }
        return new TokenStreamComponents(src, tok);
    }
}

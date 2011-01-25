package org.exist.indexing.lucene;

import org.w3c.dom.Element;
import org.exist.util.DatabaseConfigurationException;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;

import java.util.Map;
import java.util.TreeMap;

public class AnalyzerConfig {

	private static final Logger LOG = Logger.getLogger(AnalyzerConfig.class);
	
    private final static String ID_ATTRIBUTE = "id";
    private final static String CLASS_ATTRIBUTE = "class";

    private Map<String, Analyzer> analyzers = new TreeMap<String, Analyzer>();
    private Analyzer defaultAnalyzer = null;

    public Analyzer getAnalyzerById(String id) {
        return analyzers.get(id);
    }

    public Analyzer getDefaultAnalyzer() {
        return defaultAnalyzer;
    }
    
    public void addAnalyzer(Element config) throws DatabaseConfigurationException {
        String id = config.getAttribute(ID_ATTRIBUTE);
        Analyzer analyzer = configureAnalyzer(config);
        if (analyzer == null)
        	return;
        if (id == null || id.length() == 0)
            defaultAnalyzer = analyzer;
        else
            analyzers.put(id, analyzer);
    }

    protected static Analyzer configureAnalyzer(Element config) throws DatabaseConfigurationException {
        String className = config.getAttribute(CLASS_ATTRIBUTE);
        if (className != null && className.length() != 0) {
            try {
                Class<?> clazz = Class.forName(className);
                if (!Analyzer.class.isAssignableFrom(clazz)) {
                    LOG.warn("Lucene index: analyzer class has to be" +
                            " a subclass of " + Analyzer.class.getName());
                    return null;
                }
                return (Analyzer) clazz.newInstance();
            } catch (ClassNotFoundException e) {
                LOG.warn("Lucene index: analyzer class " + className + " not found.");
            } catch (IllegalAccessException e) {
                LOG.warn("Exception while instantiating analyzer class " + className + ": " + e.getMessage(), e);
            } catch (InstantiationException e) {
                LOG.warn("Exception while instantiating analyzer class " +
                        className + ": " + e.getMessage(), e);
            }
        }
        return null;
    }
}

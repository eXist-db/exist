package org.exist.indexing.spatial;

import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

/**
 */
public class GMLIndexConfig {

	private static final Logger LOG = Logger.getLogger(GMLIndexConfig.class);
	
	private final static String FLUSH_AFTER = "flushAfter";
	
	private int flushAfter = -1;
	
	public GMLIndexConfig(Map namespaces, Element node) throws DatabaseConfigurationException {
        //TODO : something useful here
    	String param = ((Element)node).getAttribute(FLUSH_AFTER);
        if (param != null) {
        	try {
        		flushAfter = Integer.parseInt(param);
        	} catch (NumberFormatException e) {
        		LOG.error("Invalid value for '" + FLUSH_AFTER + "'", e);
        	}
        }	    	
    }
	
	public int getFlushAfter() {
		return flushAfter;
	}

}

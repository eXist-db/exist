/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.md;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ExtractorConfig {
	
	private final static String CONFIG_ROOT = "extractor";
	private final static String EXTRACT_ELEMENT = "extract";
	
	private final static Logger LOG = Logger.getLogger(ExtractorConfig.class);
	
    private Map<QName, ExtractConfig> paths = new TreeMap<QName, ExtractConfig>();
    private List<ExtractConfig> wildcardPaths = new ArrayList<ExtractConfig>();

	private PathIterator iterator = new PathIterator();

    public ExtractorConfig(NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        parseConfig(configNodes, namespaces);
    }
    
    protected void parseConfig(NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                try {
					if (CONFIG_ROOT.equals(node.getLocalName())) {

					    parseConfig(node.getChildNodes(), namespaces);
                                                
					} else if (EXTRACT_ELEMENT.equals(node.getLocalName())) {

						ExtractConfig config = new ExtractConfig((Element) node, namespaces);
						
						// register either by QName or path
						if (config.getNodePath().hasWildcard()) {
							wildcardPaths.add(config);
						} else {
						    ExtractConfig idxConf = paths.get(config.getNodePath().getLastComponent());
						    if (idxConf == null) {
                                paths.put(config.getNodePath().getLastComponent(), config);
                            }
						    else {
                                idxConf.add(config);
                            }
						}

					}
                } catch (DatabaseConfigurationException e) {
					LOG.warn("Invalid metadata configuration element: " + e.getMessage());
				}
            }
        }
    }
    
    protected ExtractConfig getWildcardConfig(NodePath path) {
        ExtractConfig config;
        for (int i = 0; i < wildcardPaths.size(); i++) {
            config = wildcardPaths.get(i);
            if (config.match(path))
                return config;
        }
        return null;
    }

	public boolean isIgnoredNode(QName name) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean isInlineNode(QName name) {
		// TODO Auto-generated method stub
		return false;
	}

	public Iterator<ExtractConfig> getConfig(NodePath path) {
		iterator.reset(path);
		return iterator;
	}
	
    private class PathIterator implements Iterator<ExtractConfig> {

        private ExtractConfig nextConfig;
        private NodePath path;
        private boolean atLast = false;

        protected void reset(NodePath path) {
            this.atLast = false;
            this.path = path;
            nextConfig = paths.get(path.getLastComponent());
            if (nextConfig == null) {
                nextConfig = getWildcardConfig(path);
                atLast = true;
            }
        }

        @Override
        public boolean hasNext() {
            return (nextConfig != null);
        }

        @Override
        public ExtractConfig next() {
            if (nextConfig == null)
                return null;

            ExtractConfig currentConfig = nextConfig;
            nextConfig = nextConfig.getNext();
            if (nextConfig == null && !atLast) {
                nextConfig = getWildcardConfig(path);
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

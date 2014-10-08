/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04,  The eXist Project
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.storage;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;


/**
 * Contains information about which parts of a document should be
 * fulltext-indexed for a specified doctype. It basically keeps a list of paths
 * to include and exclude from indexing. Paths are specified using
 * simple XPath syntax, e.g. //SPEECH will select any SPEECH elements,
 * //title/@id will select all id attributes being children of title elements.
 *
 * <pre>
 *      &lt;fulltext default="all" attributes="no">
 *          &lt;include path="//div/para"/>
 *          &lt;include path="//nested" content="mixed"/>
 *      &lt;/fulltext>
 * </pre>
 *
 * @author Wolfgang Meier
 */
public class FulltextIndexSpec {
	
    private static final String PATH_ATTRIB = "path";
    private static final String CONTENT_ATTRIB = "content";
    private static final String CONTENT_MIXED = "mixed";
    private static final String PRESERVE_CONTENT_ELEMENT = "preserveContent";
    private static final String EXCLUDE_INTERFACE = "exclude";
    private static final String INCLUDE_ELEMENT = "include";
    private static final String ALPHANUM_ATTRIB = "alphanum";
    private static final String ATTRIBUTES_ATTRIB = "attributes";
    private static final String DEFAULT_ATTRIB = "default";
    private static final String CREATE_ELEMENT = "create";
    private static final String QNAME_ATTRIB = "qname";

    private static final NodePath[] ARRAY_TYPE = new NodePath[0];
    
    @SuppressWarnings("unused")
	private final static Logger LOG = Logger.getLogger(FulltextIndexSpec.class);
    
    protected NodePath[] includePath;
    protected NodePath[] excludePath;
    protected NodePath[] mixedPath;
    protected NodePath[] preserveContent;
    protected Map<QName, QNameSpec> qnameSpecs = new TreeMap<QName, QNameSpec>();

    protected boolean includeByDefault = true;
    protected boolean includeAttributes = true;
    protected boolean includeAlphaNum = true;

    /**
     * Constructor for the IndexPaths object
     *
     * param def if set to true, include everything by default. In this case
     * use exclude elements to specify the excluded parts.
     */
    public FulltextIndexSpec(Map<String, String> namespaces, Element node) throws DatabaseConfigurationException {
        includeByDefault = true;
        final ArrayList<NodePath> includeList = new ArrayList<NodePath>();
        final ArrayList<NodePath> excludeList = new ArrayList<NodePath>();
        final ArrayList<NodePath> preserveList = new ArrayList<NodePath>();
        final ArrayList<NodePath> mixedList = new ArrayList<NodePath>();

        // check default settings
        final String def = node.getAttribute(DEFAULT_ATTRIB);
        if(def != null && def.length() > 0) {
            includeByDefault = "all".equals(def);
        }
        final String indexAttributes = node.getAttribute(ATTRIBUTES_ATTRIB);
		if (indexAttributes != null && indexAttributes.length() > 0) {
			includeAttributes = "true".equals(indexAttributes) || "yes".equals(indexAttributes);
		}

		final String indexAlphaNum = node.getAttribute(ALPHANUM_ATTRIB);
		if (indexAlphaNum != null && indexAlphaNum.length() > 0)
			setIncludeAlphaNum(indexAlphaNum.equals("true") || "yes".equals(indexAlphaNum));

		// check paths to include/exclude
		final NodeList children = node.getChildNodes();
		String ps, content;
		Node next;
        Element elem;
        for(int j = 0; j < children.getLength(); j++) {
		    next = children.item(j);
		    if(INCLUDE_ELEMENT.equals(next.getLocalName())) {
                elem = (Element) next;
                content = elem.getAttribute(CONTENT_ATTRIB);
                ps = elem.getAttribute(PATH_ATTRIB);
                if (ps == null || ps.length() == 0) {
                    throw new DatabaseConfigurationException("include element requires an attribute 'path' in collection configuration.");
                }
                if (content != null && content.length() != 0 && CONTENT_MIXED.equals(content)) {
                    mixedList.add(new NodePath(namespaces, ps, false));
                } else {
                    includeList.add( new NodePath(namespaces, ps) );
                }
            } else if(EXCLUDE_INTERFACE.equals(next.getLocalName())) {
		        ps = ((Element) next).getAttribute(PATH_ATTRIB);
                if (ps == null || ps.length() == 0) {
                    throw new DatabaseConfigurationException("exclude element requires an attribute 'path' in collection configuration.");
                }
                excludeList.add( new NodePath(namespaces, ps) );
            } else if(PRESERVE_CONTENT_ELEMENT.equals(next.getLocalName())) {
		        ps = ((Element) next).getAttribute(PATH_ATTRIB);
                if (ps == null || ps.length() == 0) {
                    throw new DatabaseConfigurationException("preserveContent element requires an attribute 'path' in collection configuration.");
                }
                preserveList.add( new NodePath(namespaces, ps) );
		    } else if(CREATE_ELEMENT.equals(next.getLocalName())) {
                elem = (Element) next;
                String name = elem.getAttribute(QNAME_ATTRIB);
                if (name == null || name.length() == 0) {
                    throw new DatabaseConfigurationException("create element requires an attribute 'qname' in collection configuration.");
                }
                boolean isAttribute = false;
                if (name.startsWith("@")) {
                    isAttribute = true;
                    name = name.substring(1);
                }
                final String prefix = QName.extractPrefix(name);
                final String localName = QName.extractLocalName(name);
                String namespaceURI = "";
                if (prefix != null) {
                    namespaceURI = (String) namespaces.get(prefix);
                    if(namespaceURI == null) {
                        throw new DatabaseConfigurationException("No namespace defined for prefix: " + prefix +
                                " in index definition");
                    }
                }
                final QName qname = new QName(localName, namespaceURI, null);
                if (isAttribute)
                    {qname.setNameType(ElementValue.ATTRIBUTE);}
                qnameSpecs.put(qname, new QNameSpec(qname, elem));
            }
		}
        includePath = includeList.toArray(ARRAY_TYPE);
        excludePath = excludeList.toArray(ARRAY_TYPE);
        preserveContent = preserveList.toArray(ARRAY_TYPE);
        mixedPath = mixedList.toArray(ARRAY_TYPE); 
    }

    public List<QName> getIndexedQNames() {
        final ArrayList<QName> qnames = new ArrayList<QName>(qnameSpecs.size());
        for (final QName qname : qnameSpecs.keySet()) {
            qnames.add(qname);
        }
        return qnames;
    }

	/**
	 * @return False if all elements are indexed, True if indexation is selective.
	 */
	public boolean isSelective() {
		if((includeByDefault && excludePath.length > 0) ||
			((!includeByDefault) && includePath.length > 0))
			{return true;}
		return false;
	}

    /**
     * Include alpha-numeric data, i.e. numbers, serials, URLs and so on?
     *
     * @param index include alpha-numeric data
     */
    private void setIncludeAlphaNum( boolean index ) {
        includeAlphaNum = index;
    }

    /**
     * Include alpha-numeric data?
     */
    public boolean getIncludeAlphaNum(  ) {
        return includeAlphaNum;
    }
	
    /**
     * Check if a given path should be indexed.
     *
     * @param path path to the node
     *
     * @return Description of the Return Value
     */
    public boolean match( NodePath path ) {
        if ( includeByDefault ) {
            // check exclusions
            for (int i = 0; i < excludePath.length; i++)
                if( excludePath[i].match(path) )
                    {return false;}
                
            return true;
        }

        for (int i = 0; i < includePath.length; i++) {
            if( includePath[i].match(path) )
                {return true;}
        }
        return false;
    }
    
	/**
     * Check if a given path should be indexed.
     *
     * @param path path to the node
     *
     * @return Description of the Return Value
     */
    public boolean matchAttribute( NodePath path ) {
        if ( includeAttributes) {
            // check exclusions
            for (int i = 0; i < excludePath.length; i++)
                if( excludePath[i].match(path) )
                    {return false;}
                
            return true;
        }

        for (int i = 0; i < includePath.length; i++) {
            if( includePath[i].match(path) )
                {return true;}
        }
        return false;
    }

    /**
     * Check if the element corresponding to the given path
     * should be indexed as an element with mixed content,
     * i.e. the string value of the element will be indexed
     * as a single text sequence. Descendant elements will be ignored and
     * will not break the text into chunks.
     *
     * Example: a mixed content index on the element
     * <![CDATA[<mixed><s>un</s>even</mixed>]]> 
     *
     * @param path
     */
    public boolean matchMixedElement(NodePath path) {
        for (int i = 0; i < mixedPath.length; i++) {
            if( mixedPath[i].match(path) )
                {return true;}
        }
        return false;
    }

    public boolean hasQNameIndex(QName qname) {
        return qnameSpecs.containsKey(qname);
    }

    public boolean preserveMixedContent(QName qname) {
        final QNameSpec spec = qnameSpecs.get(qname);
        if (spec != null) {
            return spec.hasMixedContent();
        }
        return false;
    }

    /**
     * Check if a given path should be preserveContent.
     *
     * @param path path to the node
     *
     * @return Description of the Return Value
     */

      public boolean preserveContent( NodePath path ) {
          for (int i = 0; i < preserveContent.length; i++) { 
              if( preserveContent[i].match(path) )
                  {return true;}
          }
          return false;
    }
      
      public String toString() {
    	  final StringBuilder result = new StringBuilder("Full-text index\n");
    	  result.append("\tincludeByDefault : ").append(includeByDefault).append('\n');
    	  result.append("\tincludeAttributes : ").append(includeAttributes).append('\n');
    	  result.append("\tincludeAlphaNum : ").append(includeAlphaNum).append('\n');
    	  if (includePath != null) {
  	  		for (int i = 0 ; i < includePath.length; i++) {
  	  			final NodePath path = includePath[i];
  				if (path != null) 
  					{result.append("\tinclude : ").append(path.toString()).append('\n');}   	  
  	  		}
      	  }
    	  if (excludePath != null) {
			for (int i = 0 ; i < excludePath.length; i++) {
				final NodePath path = excludePath[i];
				if (path != null) 
					{result.append("\texclude : ").append(path.toString()).append('\n');}   	  
			}
    	  }  
    	  if (preserveContent != null) {
    	  		for (int i = 0 ; i < preserveContent.length; i++) {
    	  			final NodePath path = preserveContent[i];
    				if (path != null) 
    					{result.append("\tpreserve content : ").append(path.toString()).append('\n');}   	  
    	  		}
    	  }  
    	  for (final QNameSpec spec : qnameSpecs.values()) {
    		  result.append("\tQName : ").append(spec).append('\n');   
    	  }
    	  return result.toString();
      }

    private static class QNameSpec implements Comparable<QNameSpec> {

        private QName qname;
        private boolean mixedContent = false;
        private Set<String> preserve = new HashSet<String>();

        QNameSpec(QName qname, Element node) {
            this.qname = qname;
            String attr = node.getAttribute(CONTENT_ATTRIB);
            if (attr != null && attr.length() > 0) {
                this.mixedContent = CONTENT_MIXED.equalsIgnoreCase(attr);
                if (!mixedContent) {
                    attr = node.getAttribute("preserve");
                    if (attr != null && attr.length() > 0) {
                        final StringTokenizer tok = new StringTokenizer(attr, ",;: \n\t");
                        while (tok.hasMoreTokens()) {
                            preserve.add(tok.nextToken());
                        }
                    }
                }
            }
        }

        public boolean hasMixedContent() {
            return mixedContent;
        }

        @SuppressWarnings("unused")
		public Set<String> getPreserve() {
            return preserve;
        }

        public boolean equals(Object obj) {
            return ((QNameSpec) obj).qname.equals(qname);
        }

        public int compareTo(QNameSpec other) {
            return other.qname.compareTo(qname);
        }

        public String toString() {
            return qname.getStringValue() + " [" + mixedContent + ']';
        }
    }
}

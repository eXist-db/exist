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

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * IndexPaths contains information about which parts of a document should be
 * fulltext-indexed for a specified doctype. It basically keeps a list of paths
 * to include and exclude from indexing. Paths are specified using
 * simple XPath syntax, e.g. //SPEECH will select any SPEECH elements,
 * //title/@id will select all id attributes being children of title elements.
 *
 * @author Wolfgang Meier
 */
public class FulltextIndexSpec {
	
    private final static Logger LOG = Logger.getLogger(FulltextIndexSpec.class);
    
    protected ArrayList includePath;
    protected ArrayList excludePath;
    protected ArrayList preserveContent;
    
    protected boolean includeByDefault = true;
    protected boolean includeAttributes = true;
    protected boolean includeAlphaNum = true;
    
	protected int depth = 1;
	
    /**
     * Constructor for the IndexPaths object
     *
     * @param def if set to true, include everything by default. In this case
     * use exclude elements to specify the excluded parts.
     */
    public FulltextIndexSpec( boolean def ) {
        includeByDefault = def;
        includePath = new ArrayList(  );
        excludePath = new ArrayList(  );
        preserveContent = new ArrayList(  );
    }

    public FulltextIndexSpec(Element node) {
        this(true);
        String def = node.getAttribute("default");
        if(def != null && def.length() > 0)
            includeByDefault = def.equals("all");
        String indexAttributes = node.getAttribute("attributes");
		if (indexAttributes != null && indexAttributes.length() > 0)
			setIncludeAttributes(indexAttributes.equals("true"));

		String indexAlphaNum = node.getAttribute("alphanum");
		if (indexAlphaNum != null && indexAlphaNum.length() > 0)
			setIncludeAlphaNum(indexAlphaNum.equals("true"));

		String indexDepth = node.getAttribute("index-depth");
		if (indexDepth != null && indexDepth.length() > 0)
			try {
				int depth = Integer.parseInt(indexDepth);
				setIndexDepth(depth);
			} catch (NumberFormatException e) {
			}

		NodeList include = node.getElementsByTagName("include");
		String ps;
		for (int j = 0; j < include.getLength(); j++) {
			ps = ((Element) include.item(j)).getAttribute("path");
			addInclude(ps);
		}
		
		NodeList exclude = node.getElementsByTagName("exclude");

		for (int j = 0; j < exclude.getLength(); j++) {
			ps = ((Element) exclude.item(j)).getAttribute("path");
			addExclude(ps);
		}

		NodeList preserveContent = node.getElementsByTagName("preserveContent");

		for (int j = 0; j < preserveContent.getLength(); j++) {
			ps = ((Element) preserveContent.item(j)).getAttribute("path");
			addpreserveContent(ps);
		}
    }
    
    /**
     * Add a path to the list of includes
     *
     * @param path The feature to be added to the Include attribute
     */
    public void addInclude( String path ) {
        includePath.add( new NodePath(path) );
    }

    /**
     * Add a path to the list of excludes
     *
     * @param path DOCUMENT ME!
     */
    public void addExclude( String path ) {
        excludePath.add( new NodePath(path) );
    }

	/**
	 * Returns false if all elements are indexed, true 
	 * if indexation is selective.
	 * 
	 * @return
	 */
	public boolean isSelective() {
		if((includeByDefault && excludePath.size() > 0) ||
			((!includeByDefault) && includePath.size() > 0))
			return true;
		return false;
	}
	
    /**
     * Include attribute values?
     *
     * @param index The new includeAttributes value
     */
    public void setIncludeAttributes( boolean index ) {
        includeAttributes = index;
    }

    /**
     * Include attribute values?
     *
     * @return The includeAttributes value
     */
    public boolean getIncludeAttributes(  ) {
        return includeAttributes;
    }

    /**
     * Include alpha-numeric data, i.e. numbers, serials, URLs and so on?
     *
     * @param index include alpha-numeric data
     */
    public void setIncludeAlphaNum( boolean index ) {
        includeAlphaNum = index;
    }

    /**
     * Include alpha-numeric data?
     *
     * @return 
     */
    public boolean getIncludeAlphaNum(  ) {
        return includeAlphaNum;
    }

	public int getIndexDepth() {
		return depth;
	}
	
	public void setIndexDepth( int depth ) {
		this.depth = depth;
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
            for ( Iterator i = excludePath.iterator(); i.hasNext(  ); )
                if( ((NodePath)i.next()).match(path) )
                    return false;
                
            return true;
        }

        for ( Iterator i = includePath.iterator(); i.hasNext(); )
            if( ((NodePath)i.next()).match(path) )
                return true;

        return false;
    }

    /**
     * Add a path to the list of node with preserveContent option
     *
     * @param path DOCUMENT ME!
     */
    public void addpreserveContent( String path ) {
    	preserveContent.add( new NodePath(path) );
    }
    
    
    /**
     * Check if a given path should be preserveContent.
     *
     * @param path path to the node
     *
     * @return Description of the Return Value
     */

      public boolean preserveContent( NodePath path ) {
     	
    	for ( Iterator i = preserveContent.iterator(); i.hasNext(); ) 
            if( ((NodePath)i.next()).match(path) )
                return true; 

        return false;
    }



}

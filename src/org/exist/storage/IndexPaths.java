/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang Meier
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
 *  $Id:
 */
package org.exist.storage;

import it.unimi.dsi.fastUtil.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Iterator;
import org.exist.util.FastStringBuffer;


/**
 * IndexPaths contains information about which parts of a document should be
 * fulltext-indexed for a specified doctype. It basically keeps a list of paths
 * to include and paths to exclude from indexing. Paths are specified using
 * simple XPath syntax, e.g. //SPEECH will select any SPEECH elements,
 * //title/@id will select all id attributes being children of title elements.
 *
 * @author Wolfgang Meier
 */
public class IndexPaths {
	
	private final static Object2ObjectOpenHashMap cache =
		new Object2ObjectOpenHashMap();
	private final static int MAX_CACHE_SIZE = 64;
	
    protected ArrayList includePath;
    protected ArrayList excludePath;
    protected boolean includeByDefault = true;
    protected boolean includeAttributes = true;
    protected boolean includeAlphaNum = true;
	protected int depth = 1;
	private FastStringBuffer token = new FastStringBuffer();
	
    /**
     * Constructor for the IndexPaths object
     *
     * @param def if set to true, include everything by default. In this case
     * use exclude elements to specify the excluded parts.
     */
    public IndexPaths( boolean def ) {
        includeByDefault = def;
        includePath = new ArrayList(  );
        excludePath = new ArrayList(  );
    }

    /**
     * Add a path to the list of includes
     *
     * @param path The feature to be added to the Include attribute
     */
    public void addInclude( String path ) {
        ArrayList a = tokenize( path );
        String arr[] = new String[a.size()];
        arr = (String[])a.toArray(arr);
        includePath.add( arr );
    }

    /**
     * Add a path to the list of excludes
     *
     * @param path DOCUMENT ME!
     */
    public void addExclude( String path ) {
		ArrayList a = tokenize( path );
		String arr[] = new String[a.size()];
		arr = (String[])a.toArray(arr);
        excludePath.add( arr );
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
    public boolean match( String path ) {
		ArrayList temp = tokenize(path);
        if ( includeByDefault ) {
            // check exclusions
            for ( Iterator i = excludePath.iterator(  ); i.hasNext(  ); )
                if ( IndexPaths.match( (String[]) i.next(  ), temp ) )
                    return false;

            return true;
        }

        for ( Iterator i = includePath.iterator(  ); i.hasNext(  ); )
            if ( IndexPaths.match( (String[]) i.next(  ), temp ) )
                return true;

        return false;
    }

    /**
     * Description of the Method
     *
     * @param path Description of the Parameter
     * @param other Description of the Parameter
     *
     * @return Description of the Return Value
     */
    private final static boolean match( String[] path, ArrayList other) {
        int x = 0;
        int y = 0;
        boolean skip = false;
		final int otherLen = other.size();
		final int pathLen = path.length;
        while ( x < otherLen ) {
            if ( y == pathLen )
                return true;

            if ( path[y].equals( "*" ) ) {
                y++;
                skip = true;
            }

            if ( other.get(x).equals( path[y] ) ) {
                y++;
                x++;
                skip = false;
            } else if ( skip )
                x++;
            else

                return false;
        }

        return ( y == pathLen );
    }

    /**
     * Description of the Method
     *
     * @param path Description of the Parameter
     *
     * @return Description of the Return Value
     */
    private final ArrayList tokenize( String path ) {
    	ArrayList temp;
    	synchronized(cache) {
    		temp = (ArrayList)cache.get(path);
    		if(temp != null)
    			return temp;
    	}
    	temp = new ArrayList();
        String next;
       	token.reset();
        int pos = 0;
		char ch = 0;
		final int pathLen = path.length();
        while ( pos < pathLen ) {
			ch = path.charAt(pos);
            switch ( ch ) {
            case '/':
                next = token.toString();
                token.reset();
                if ( next.length(  ) > 0 )
                    temp.add( next );
                if ( path.charAt( ++pos ) == '/' )
                    temp.add( "*" );
                break;

            default:
                token.append(ch);
                pos++;
            }
        }
        if ( token.length() > 0 )
            temp.add( token.toString() );
        synchronized(cache) {
        	if(cache.size() == MAX_CACHE_SIZE)
        		cache.clear();
        	cache.put(path, temp);
        }
        return temp;
    }
}

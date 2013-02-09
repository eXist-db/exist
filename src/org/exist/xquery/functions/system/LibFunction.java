/*
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id: RenderFunction.java 10610 2009-11-26 09:12:00Z shabanovd $
 */

package org.exist.xquery.functions.system;


import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.exist.start.LatestFileResolver;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;

/**
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 */
public abstract class LibFunction extends BasicFunction {
	
    private final static String   LIB_WEBINF = "WEB-INF/lib/";
    private final static String[] LIB  = {"./lib/core", "./lib/optional", "./lib/extensions", "./lib/user", "."};
    
    private static Map<String, File> allFiles = new HashMap<String, File>();
    
    private static LatestFileResolver libFileResolver = new LatestFileResolver();
    
	private File[] libFolders;

	public LibFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
		libFolders = libFolders(context.getBroker().getConfiguration().getExistHome());
	}

    private File[] libFolders(File contextRoot){
        
    	File[] libFolders;
        
        // Setup path based on installation (in jetty, container)
        if(isInWarFile(contextRoot)){
            // all files mixed in contextRoot/WEB-INF/lib
            libFolders = new File[]{new File(contextRoot, LIB_WEBINF)};
            
        } else {
            //files located in contextRoot/lib/* and contextRoot
        	libFolders = new File[LIB.length];
        	for (int i=0; i<LIB.length; i++){
        		libFolders[i] = new File(contextRoot, LIB[i]);
        	}
        }
        
        return libFolders;
    }
    
    private boolean isInWarFile(File existHome){
        
        if( new File(existHome, LIB[0]).isDirectory() ) {
            return false;
        }
        return true;
    }
    
    private File getLib(File folder, String libFileBaseName){
        final String fileToFind = folder.getAbsolutePath() + File.separatorChar + libFileBaseName;
        final String resolvedFile = libFileResolver.getResolvedFileName(fileToFind);
        final File lib = new File(resolvedFile);
        if (lib.exists()) {
            return lib;
        } else {
            return null;
        }
    }

    protected File getLib(String key){
    	File retVal = allFiles.get(key);
    	if (allFiles.keySet().contains(key)){
    		return retVal; 
    	}
        for (final File libFolder : libFolders){
        	retVal = getLib(libFolder, key);
        	if (retVal != null){
        		break;
        	}
        }
		allFiles.put(key, retVal);
        return retVal;
    }
    
}
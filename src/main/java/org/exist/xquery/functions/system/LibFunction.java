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


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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
    
    private static Map<String, Path> allFiles = new HashMap<String, Path>();
    
    private static LatestFileResolver libFileResolver = new LatestFileResolver();
    
	private Stream<Path> libFolders;

	public LibFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
        libFolders = context.getBroker().getConfiguration().getExistHome().map(this::libFolders).orElse(Stream.empty());
	}

    private Stream<Path> libFolders(final Path contextRoot){
        // Setup path based on installation (in jetty, container)
        if(isInWarFile(contextRoot)){
            // all files mixed in contextRoot/WEB-INF/lib
            return Stream.of(contextRoot.resolve(LIB_WEBINF));
            
        } else {
            //files located in contextRoot/lib/* and contextRoot
            return Arrays.stream(LIB)
                    .map(contextRoot::resolve);
        }
    }
    
    private boolean isInWarFile(Path existHome){
        return !Files.isDirectory(existHome.resolve(LIB[0]));
    }
    
    private Optional<Path> getLib(final Path folder, final String libFileBaseName){
        final String fileToFind = folder.toAbsolutePath().resolve(libFileBaseName).toString();
        final String resolvedFile = libFileResolver.getResolvedFileName(fileToFind);
        return Optional.of(Paths.get(resolvedFile)).filter(Files::exists);
    }

    protected Path getLib(final String key){
    	Path retVal = allFiles.get(key);
    	if (allFiles.keySet().contains(key)){
    		return retVal; 
    	}

        final Optional<Optional<Path>> libVal = libFolders.map(libFolder -> getLib(libFolder, key)).filter(Optional::isPresent).findFirst();

        if(libVal.isPresent()) {
            if(libVal.get().isPresent()) {
                retVal = libVal.get().get();
            }
        }

        allFiles.put(key, retVal);
        return retVal;
    }
    
}
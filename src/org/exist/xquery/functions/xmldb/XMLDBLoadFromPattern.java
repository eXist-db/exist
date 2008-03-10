/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import java.io.File;
import java.util.StringTokenizer;

import org.exist.dom.QName;
import org.exist.util.DirectoryScanner;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author wolf
 */
public class XMLDBLoadFromPattern extends XMLDBAbstractCollectionManipulator {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName("store-files-from-pattern", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                "Store new resources into the database. Resources are read from the server's " +
                "file system, using file patterns. " +
                "The first argument denotes the collection where resources should be stored. " +
                "The collection can be either specified as a simple collection path or " +
                "an XMLDB URI. " +
                "The second argument is the directory in the file system wherefrom the files are read." +
                "The third argument is the file pattern. File pattern matching is based " +
                "on code from Apache's Ant, thus following the same conventions. For example: " +
                "*.xml matches any file ending with .xml in the current directory, **/*.xml matches files " +
                "in any directory below the current one. " +
                "The function returns a sequence of all document paths added " +
                "to the db. These can be directly passed to fn:doc() to retrieve the document.",
                new SequenceType[] {
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)
                },
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)),
        new FunctionSignature(
                new QName("store-files-from-pattern", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                "Store new resources into the database. Resources are read from the server's " +
                "file system, using file patterns. " +
                "The first argument denotes the collection where resources should be stored. " +
                "The collection can be either specified as a simple collection path or " +
                "an XMLDB URI. " +
                "The second argument is the directory in the file system wherefrom the files are read." +
                "The third argument is the file pattern. File pattern matching is based " +
                "on code from Apache's Ant, thus following the same conventions. For example: " +
                "*.xml matches any file ending with .xml in the current directory, **/*.xml matches files " +
                "in any directory below the current one. " +
                "The fourth argument $d is used to specify a mime-type.  If the mime-type " +
                "is something other than 'text/xml' or 'application/xml', the resource will be stored as " +
                "a binary resource." +
                "The function returns a sequence of all document paths added " +
                "to the db. These can be directly passed to fn:doc() to retrieve the document.",
                new SequenceType[] {
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                    new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
                    new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
                },
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)),
        new FunctionSignature(
                new QName("store-files-from-pattern", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                "Store new resources into the database. Resources are read from the server's " +
                "file system, using file patterns. " +
                "The first argument denotes the collection where resources should be stored. " +
                "The collection can be either specified as a simple collection path or " +
                "an XMLDB URI. " +
                "The second argument is the directory in the file system wherefrom the files are read." +
                "The third argument is the file pattern. File pattern matching is based " +
                "on code from Apache's Ant, thus following the same conventions. For example: " +
                "*.xml matches any file ending with .xml in the current directory, **/*.xml matches files " +
                "in any directory below the current one. " +
                "The fourth argument $d is used to specify a mime-type.  If the mime-type " +
                "is something other than 'text/xml' or 'application/xml', the resource will be stored as " +
                "a binary resource." +
                "If the final boolean argument is true(), the directory structure will be kept in the collection, " +
                "otherwise all the matching resources, including the ones in sub-directories, will be stored " +
                "in the collection given in the first argument flatly." +
                "The function returns a sequence of all document paths added " +
                "to the db. These can be directly passed to fn:doc() to retrieve the document.",
                new SequenceType[] {
            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
        },
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE))
    };

    public XMLDBLoadFromPattern(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

        /* (non-Javadoc)
         * @see org.exist.xquery.functions.xmldb.XMLDBAbstractCollectionManipulator#evalWithCollection(org.xmldb.api.base.Collection, org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
         */
    protected Sequence evalWithCollection(Collection collection, Sequence[] args,
            Sequence contextSequence) throws XPathException {
        File baseDir = new File(args[1].getStringValue());
        LOG.debug("Loading files from directory: " + baseDir);
        Sequence patterns = args[2];
        String resourceType = "XMLResource";
        String mimeType = MimeType.XML_TYPE.getName();
        boolean keepDirStructure = false;
        if(getSignature().getArgumentCount() > 3) {
            mimeType = args[3].getStringValue();
	    MimeType mime = MimeTable.getInstance().getContentType(mimeType);
	    
            if(mime != null && !mime.isXMLType())
                resourceType = "BinaryResource";
        }
        if (getSignature().getArgumentCount() == 5)
            keepDirStructure = args[4].effectiveBooleanValue();
        
        ValueSequence stored = new ValueSequence();
        for(SequenceIterator i = patterns.iterate(); i.hasNext(); ) {
            String pattern = i.nextItem().getStringValue();
            File[] files = DirectoryScanner.scanDir(baseDir, pattern);
            LOG.debug("Found: " + files.length);
            Collection col = collection;
            String relDir, prevDir = null;
            for(int j = 0; j < files.length; j++) {
                try {
                    LOG.debug(files[j].getAbsolutePath());
                    String relPath = files[j].toString().substring( baseDir.toString().length() );
                    int p = relPath.lastIndexOf( File.separatorChar );
					
					if( p >= 0 ) {
	                    relDir = relPath.substring( 0, p );
	                    relDir = relDir.replace(File.separatorChar, '/');
					} else {
						relDir = relPath;
					}
					
                    if ( keepDirStructure && ( prevDir == null || ( !relDir.equals(prevDir) ) ) ) {
                        col = makeColl(collection, relDir);
                        prevDir = relDir;
                    }
                    //TODO  : these probably need to be encoded
                    Resource resource =
                            col.createResource(files[j].getName(), resourceType);
                    resource.setContent(files[j]);
                    if("BinaryResource".equals(resourceType))
                        ((EXistResource)resource).setMimeType(mimeType);
                    col.storeResource(resource);
                    //TODO : use dedicated function in XmldbURI
                    stored.add(new StringValue(col.getName() + "/" + resource.getId()));
                } catch (XMLDBException e) {
                    LOG.warn("Could not store file " + files[j].getAbsolutePath() +
                            ": " + e.getMessage(), e);
                }
            }
        }
        return stored;
    }

    private final Collection makeColl(Collection parentColl, String relPath)
    throws XMLDBException {
        CollectionManagementService mgtService;
        Collection current = parentColl, c;
        String token;
        StringTokenizer tok = new StringTokenizer(relPath, "/");
        while (tok.hasMoreTokens()) {
            token = tok.nextToken();
            c = current.getChildCollection(token);
            if (c == null) {
                mgtService = (CollectionManagementService) current.getService("CollectionManagementService", "1.0");
                current = mgtService.createCollection(token);
            } else
                current = c;
        }
        return current;
    }

}
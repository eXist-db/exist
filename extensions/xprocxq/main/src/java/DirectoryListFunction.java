/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2008 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id: DirectoryListFunction.java 9401 2009-07-18 10:34:29Z cutlass $
 */
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.DirectoryScanner;
import org.apache.log4j.Logger;

import java.io.File;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * eXist File Module Extension DirectoryListFunction
 *
 * Enumerate a list of files, including their size and modification time, found in a specified directory, using a pattern
 *
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @author ljo
 * @author Jim Fuller -- temporary location for use with xprocxq
 * @serial 2009-06-29
 * @version 1.1
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */

public class DirectoryListFunction extends BasicFunction {

	private final static Logger logger = Logger.getLogger(DirectoryListFunction.class);

	final static String NAMESPACE_URI = UtilModule.NAMESPACE_URI;
	final static String PREFIX = UtilModule.PREFIX;


	public final static FunctionSignature[] signatures =
	{
		new FunctionSignature(
			new QName("directory-list", NAMESPACE_URI, PREFIX),
			"DO NOT USE! -- temporary only List all files, including their file size and modification time, found in a directory. Files are located in the server's " +
			"file system, using file patterns. " +
			"The first argument is the directory in the file system where the files are located." +
			"The second argument is the file pattern. File pattern matching is based " +
			"on code from Apache's Ant, thus following the same conventions. For example: " +
			"*.xml matches any file ending with .xml in the current directory, **/*.xml matches files " +
			"in any directory below the current one. " +
			"The function returns a node fragment that shows all matching filenames, including their file size and modification time, and the subdirectory they were found in",
			new SequenceType[]
			{
			    new FunctionParameterSequenceType("directory", Type.STRING, Cardinality.EXACTLY_ONE, "the base directory"),
			    new FunctionParameterSequenceType("pattern", Type.STRING, Cardinality.EXACTLY_ONE, "the file glob pattern")
				},
			new SequenceType( Type.NODE, Cardinality.ZERO_OR_ONE )
			)
		};


	/**
	 * DirectoryListFunction Constructor
	 *
	 * @param context	The Context of the calling XQuery
	 */

	public DirectoryListFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
	}


	/**
	 * evaluate the call to the XQuery execute() function,
	 * it is really the main entry point of this class
	 *
	 * @param args		arguments from the execute() function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A node representing the SQL result set
	 *
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		File baseDir = new File( args[0].getStringValue() );
		Sequence patterns = args[1];

		logger.info("Listing matching files in directory: " + baseDir);

		Sequence xmlResponse = null;

		MemTreeBuilder builder = context.getDocumentBuilder();

		builder.startDocument();
		builder.startElement( new QName( "list", NAMESPACE_URI, PREFIX ), null );
		builder.addAttribute( new QName( "directory", null, null ), baseDir.toString() );

		for( SequenceIterator i = patterns.iterate(); i.hasNext(); ) {
			String pattern 	= i.nextItem().getStringValue();
			File[] files 	= DirectoryScanner.scanDir( baseDir, pattern );
			String relDir 	= null;

			logger.info("Found: " + files.length);

			for (int j = 0; j < files.length; j++) {
				logger.info("Found: " + files[j].getAbsolutePath());

				String relPath = files[j].toString().substring(baseDir.toString().length() + 1);

				int p = relPath.lastIndexOf(File.separatorChar);

				if (p >= 0) {
					relDir = relPath.substring(0, p);
					relDir = relDir.replace(File.separatorChar, '/');
				}

				builder.startElement(new QName("file", NAMESPACE_URI, PREFIX), null);

				builder.addAttribute(new QName("name", null, null), files[j].getName());
				builder.addAttribute(new QName("size", null, null), Long.toString(files[j].length()));
				// FIXIT: return a dateTime instead of a long. /ljo
				builder.addAttribute(new QName("modified", null, null), Long.toString(files[j].lastModified()));
				if (relDir != null && relDir.length() > 0) {
					builder.addAttribute(new QName("subdir", null, null), relDir);
				}

				builder.endElement();

			}
		}

		builder.endElement();

		xmlResponse = (NodeValue) builder.getDocument().getDocumentElement();

		return(xmlResponse);
	}

}
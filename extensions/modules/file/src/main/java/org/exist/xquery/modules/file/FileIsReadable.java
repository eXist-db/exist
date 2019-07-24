/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.xquery.modules.file;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @see java.nio.file.Files#isReadable(Path)
 *
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 */
public class FileIsReadable extends BasicFunction {

	private static final Logger logger = LogManager.getLogger(FileIsReadable.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "is-readable", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Tests if a file is readable.  This method is only available to the DBA role.",
			new SequenceType[] {				
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the file" )
				},				
			new FunctionReturnSequenceType( Type.BOOLEAN, 
                    Cardinality.EXACTLY_ONE, "true if file can be read" ) )
		};

	public FileIsReadable(final XQueryContext context, final FunctionSignature signature)
	{
		super( context, signature );
	}
	
	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException
	{
		if (!context.getSubject().hasDbaRole()) {
			XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}
        
		final String inputPath = args[0].getStringValue();
        final Path file = FileModuleHelper.getFile(inputPath);
		
		return BooleanValue.valueOf(Files.isReadable(file));
	}
}

/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.file;

import java.io.IOException;
import java.nio.file.CopyOption;
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
 * @see java.nio.file.Files#move(Path, Path, CopyOption...)
 * 
 * @author Dannes Wessels
 *
 */
public class FileMove extends BasicFunction {
	
	private final static Logger logger = LogManager.getLogger(FileMove.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "move", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Move (rename) a file or directory. Exact operation is platform dependent. This " +
            "method is only available to the DBA role.",
			new SequenceType[] {				
				new FunctionParameterSequenceType( "original", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the file" ),
				new FunctionParameterSequenceType( "destination", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the file" )
				},				
			new FunctionReturnSequenceType( Type.BOOLEAN, 
                    Cardinality.EXACTLY_ONE, "true if successful, false otherwise" ) )
		};
	

	public FileMove(final XQueryContext context, final FunctionSignature signature)
	{
		super(context, signature);
	}
	
	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException
	{
		if (!context.getSubject().hasDbaRole()) {
			XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}

		Sequence moved 	= BooleanValue.FALSE;

		final String inputPath1 = args[0].getStringValue();
        final Path src = FileModuleHelper.getFile(inputPath1, this);

		final String inputPath2 = args[1].getStringValue();
        final Path dest = FileModuleHelper.getFile(inputPath2, this);

		try {
			Files.move(src, dest);
			return BooleanValue.TRUE;
		} catch(final IOException ioe) {
			LOG.error(ioe);
			return BooleanValue.FALSE;
		}
	}
}

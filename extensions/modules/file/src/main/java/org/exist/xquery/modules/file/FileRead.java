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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Pierrick Brihaye
 * @author Dizzzz
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 */
public class FileRead extends BasicFunction {

	private final static Logger logger = LogManager.getLogger(FileRead.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "read", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Reads the content of file.  This method is only available to the DBA role.",
			new SequenceType[] {				
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The directory path or URI in the file system." )
				},				
			new FunctionReturnSequenceType( Type.STRING, 
                    Cardinality.ZERO_OR_ONE, "the file contents" ) ),
        
		new FunctionSignature(
			new QName( "read", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Reads the content of file.  This method is only available to the DBA role.",
			new SequenceType[] {
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The directory path or URI in the file system." ),
				new FunctionParameterSequenceType( "encoding", Type.STRING, 
                        Cardinality.EXACTLY_ONE, "The encoding type for the file" )
				},
				new FunctionReturnSequenceType( Type.STRING, 
                        Cardinality.ZERO_OR_ONE, "the file contents" ) )
		};

	public FileRead(final XQueryContext context, final FunctionSignature signature)
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

		final String inputPath = args[0].getStringValue();
		final Path file = FileModuleHelper.getFile(inputPath, this);

		final Charset encoding;
		if(args.length == 2) {
			encoding = Charset.forName(args[1].getStringValue());
		} else {
			encoding = StandardCharsets.UTF_8;
		}

		try {
            return new StringValue(this, Files.readString(file, encoding));
		} catch(final IOException e ) {
			throw new XPathException(this, e);	
		}
	}
}

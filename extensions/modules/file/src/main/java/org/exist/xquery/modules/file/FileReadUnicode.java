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

import java.io.IOException;
import java.io.StringWriter;
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
 */
public class FileReadUnicode extends BasicFunction {

	private final static Logger logger = LogManager.getLogger(FileReadUnicode.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "read-unicode", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Reads the contents of a file.  Unicode BOM (Byte Order Marker) will be stripped " +
            "off if found.  This method is only available to the DBA role.",
			new SequenceType[] {				
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The directory path or URI in the file system." )
				},				
			new FunctionReturnSequenceType( Type.STRING, 
                    Cardinality.ZERO_OR_ONE, "the contents of the file" ) ),
        
		new FunctionSignature(
			new QName( "read-unicode", FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Reads the contents of a file.  Unicode BOM (Byte Order Marker) will be stripped " +
            "off if found.  This method is only available to the DBA role.",
			new SequenceType[] {
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The directory path or URI in the file system." ),
				new FunctionParameterSequenceType( "encoding", Type.STRING, 
                        Cardinality.EXACTLY_ONE, "The file is read with the encoding specified." )
				},
				new FunctionReturnSequenceType( Type.STRING, 
                        Cardinality.ZERO_OR_ONE, "the contents of the file" ) )
		};

	public FileReadUnicode(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
		if (!context.getSubject().hasDbaRole()) {
			XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
			logger.error("Invalid user", xPathException);
			throw xPathException;
		}

		final String inputPath = args[0].getStringValue();
        final Path file = FileModuleHelper.getFile(inputPath);

		final Charset encoding;
		if(args.length == 2) {
			encoding = Charset.forName(args[1].getStringValue());
		} else {
			encoding = StandardCharsets.UTF_8;
		}
		
		try(final UnicodeReader reader = new UnicodeReader(Files.newInputStream(file), encoding.name());
				final StringWriter sw  = new StringWriter()) {

			char[] buf = new char[1024];
			int len;
			while(( len = reader.read( buf ) ) > 0) {
				sw.write( buf, 0, len) ;
			}

			return new StringValue(sw.toString());
		} catch(final IOException e) {
			throw new XPathException(this, e);
		}
	}
}

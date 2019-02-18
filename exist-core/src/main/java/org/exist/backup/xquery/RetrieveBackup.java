/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist Project
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
 *  $Id$
 */
package org.exist.backup.xquery;

import org.exist.backup.ZipArchiveBackupDescriptor;
import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.storage.BrokerPool;
import org.exist.util.FileUtils;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;


public class RetrieveBackup extends BasicFunction
{
    public final static FunctionSignature signature = new FunctionSignature( new QName( "retrieve", BackupModule.NAMESPACE_URI, BackupModule.PREFIX ), "Retrieves a zipped backup archive, $name, and directly streams it to the HTTP response. " + "For security reasons, the function will only read .zip files in the specified directory, $directory.", new SequenceType[] {
            new FunctionParameterSequenceType( "directory", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the directory where the backup file is located." ),
            new FunctionParameterSequenceType( "name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the file to retrieve." )
        }, new SequenceType( Type.ITEM, Cardinality.EMPTY ) );

    public RetrieveBackup(final XQueryContext context )
    {
        super( context, signature );
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence ) throws XPathException
    {
        if(!context.getEffectiveUser().hasDbaRole()) {
            throw new XPathException("You must be a DBA to retrieve a backup");
        }

        final String exportDir = args[0].getStringValue();
        Path dir       = Paths.get( exportDir );

        if( !dir.isAbsolute() ) {
            dir = ((Path)context.getBroker().getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR)).resolve(exportDir);
        }
        final String name       = args[1].getStringValue();
        final Path   backupFile = dir.resolve(name);

        if( !Files.isReadable(backupFile)) {
            return( Sequence.EMPTY_SEQUENCE );
        }

        if( !name.endsWith( ".zip" ) ) {
            throw( new XPathException( this, "for security reasons, the function only allows " + "reading zipped backup archives" ) );
        }

        try {
            final ZipArchiveBackupDescriptor descriptor = new ZipArchiveBackupDescriptor( backupFile );
            final Properties                 properties = descriptor.getProperties();

            if( ( properties == null ) || (properties.isEmpty()) ) {
                throw( new XPathException( this, "the file does not see to be a valid backup archive" ) );
            }
        }
        catch( final IOException e ) {
            throw( new XPathException( this, "the file does not see to be a valid backup archive" ) );
        }

        // directly stream the backup contents to the HTTP response
        final Optional<ResponseWrapper> maybeResponse = Optional.ofNullable(context.getHttpContext())
                .map(XQueryContext.HttpContext::getResponse);

        if(!maybeResponse.isPresent()) {
            throw( new XPathException( this, "No response object found in the current XQuery context." ) );
        }

        final ResponseWrapper response =  maybeResponse.get();
        if( !"org.exist.http.servlets.HttpResponseWrapper".equals(response.getClass().getName())) {
            throw new XPathException(this, signature.toString() + " can only be used within the EXistServlet or XQueryServlet");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Length", String.valueOf(FileUtils.sizeQuietly(backupFile)));
        try {
            try(final OutputStream os  = response.getOutputStream()) {
                Files.copy(backupFile, os);
            }
            response.flushBuffer();
        }
        catch( final IOException e ) {
            throw( new XPathException( this, "An IO error occurred while reading the backup archive" ) );
        }
        return( Sequence.EMPTY_SEQUENCE );
    }
}

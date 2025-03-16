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
package org.exist.xquery.modules.image;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.journal.JournalManager;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * 
 * 
 * @author Rafael Troilo (rtroilo@gmail.com)
 * 
 */
public class GetThumbnailsFunction extends BasicFunction {

	@SuppressWarnings("unused")
	private static final Logger logger = LogManager.getLogger(GetThumbnailsFunction.class);
	
	private final static int MAXTHUMBHEIGHT = 100;

	private final static int MAXTHUMBWIDTH = 100;

	private final static String THUMBPREFIX = "";

	private final static String THUMBPATH = "thumbs";

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("thumbnail", ImageModule.NAMESPACE_URI,
					ImageModule.PREFIX),
			"Generate thumbnails from the given database collection",
			new SequenceType[] {
					new FunctionParameterSequenceType("collection", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI to the collection"),
					new FunctionParameterSequenceType("thumbnail-location", Type.ANY_URI, Cardinality.ZERO_OR_ONE, "The location in the database where the thumbnails should be created, this can be a local path, with the prefix 'xmldb:' a absolute path within the database or with 'rel:' path relative to the given $collection.  You can leave this empty then the default is 'rel:/thumbs'. "),
					new FunctionParameterSequenceType("dimension", Type.INTEGER, Cardinality.ZERO_OR_MORE, "The dimension of the thumbnails, if empty then the default values are 'maxheight = 100' and 'maxwidth = 100', the first value is 'maxheight' and the second 'maxwidth'. "),
					new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.ZERO_OR_ONE, "The prefix to append to the thumbnail filenames") },

			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the result"));

	public GetThumbnailsFunction(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		ValueSequence result = new ValueSequence();
		// boolean isDatabasePath = false;
		boolean isSaveToDataBase = false;

		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		AnyURIValue picturePath = (AnyURIValue) args[0].itemAt(0);
		if (picturePath.getStringValue().startsWith("xmldb:exist://")) {
			picturePath = new AnyURIValue(this, picturePath.getStringValue()
					.substring(14));
		}

		AnyURIValue thumbPath = null;
		if (args[1].isEmpty()) {
			thumbPath = new AnyURIValue(this, picturePath.toXmldbURI().append(
					THUMBPATH));
			isSaveToDataBase = true;
		} else {
			thumbPath = (AnyURIValue) args[1].itemAt(0);
			if (thumbPath.getStringValue().startsWith("file:")) {
				isSaveToDataBase = false;
				thumbPath = new AnyURIValue(this, thumbPath.getStringValue().substring(5));
			} else {
				isSaveToDataBase = true;
				try {
					XmldbURI thumbsURI = XmldbURI.xmldbUriFor(thumbPath.getStringValue());
					if (!thumbsURI.isAbsolute())
						thumbsURI = picturePath.toXmldbURI().append(thumbPath.toString());
					thumbPath = new AnyURIValue(this, thumbsURI.toString());
				} catch (URISyntaxException e) {
					throw new XPathException(this, e.getMessage());
				}
			}
		}

		// result.add(new StringValue(picturePath.getStringValue()));
		// result.add(new StringValue(thumbPath.getStringValue() + " isDB?= "
		// + isSaveToDataBase));

		int maxThumbHeight = MAXTHUMBHEIGHT;
		int maxThumbWidth = MAXTHUMBWIDTH;

		if (!args[2].isEmpty()) {
			maxThumbHeight = ((IntegerValue) args[2].itemAt(0)).getInt();
			if (args[2].hasMany())
				maxThumbWidth = ((IntegerValue) args[2].itemAt(1)).getInt();
		}

		String prefix = THUMBPREFIX;

		if (!args[3].isEmpty()) {
			prefix = args[3].itemAt(0).getStringValue();

		}

		// result.add(new StringValue("maxThumbHeight = " + maxThumbHeight
		// + ", maxThumbWidth = " + maxThumbWidth));

		BrokerPool pool = null;
		try {
			pool = BrokerPool.getInstance();
		} catch (Exception e) {
			result.add(new StringValue(this, e.getMessage()));
			return result;
		}

        final DBBroker dbbroker = context.getBroker();

        // Start transaction
        final TransactionManager transact = pool.getTransactionManager();
        try (final Txn transaction = transact.beginTransaction()) {

            Collection thumbCollection = null;
            Path thumbDir = null;
            if (isSaveToDataBase) {
                try {
                    thumbCollection = dbbroker.getOrCreateCollection(transaction,
                            thumbPath.toXmldbURI());
                    dbbroker.saveCollection(transaction, thumbCollection);
                } catch (Exception e) {
                    throw new XPathException(this, e.getMessage());
                }
            } else {
                thumbDir = Paths.get(thumbPath.toString());
                if (!Files.isDirectory(thumbDir))
                    try {
                        Files.createDirectories(thumbDir);
                    } catch (IOException e) {
                        throw new XPathException(this, e.getMessage());
                    }

            }

            Collection allPictures = null;
            Collection existingThumbsCol = null;
            List<Path> existingThumbsArray = null;
            try {
                allPictures = dbbroker.getCollection(picturePath.toXmldbURI());

                if (allPictures == null) {
                    return Sequence.EMPTY_SEQUENCE;
                }

                if (isSaveToDataBase) {
                    existingThumbsCol = dbbroker.getCollection(thumbPath.toXmldbURI());
                } else {
                    existingThumbsArray = FileUtils.list(thumbDir, path -> {
                        final String fileName = FileUtils.fileName(path);
                        return fileName.endsWith(".jpeg") || fileName.endsWith(".jpg");
                    });
                }
            } catch (PermissionDeniedException | IOException e) {
                throw new XPathException(this, e.getMessage(), e);
            }

            DocumentImpl docImage = null;
            BinaryDocument binImage = null;
            @SuppressWarnings("unused")
            BufferedImage bImage = null;
            @SuppressWarnings("unused")
            byte[] imgData = null;
            Image image = null;
            UnsynchronizedByteArrayOutputStream os = null;

            try {
                Iterator<DocumentImpl> i = allPictures.iterator(dbbroker);

                while (i.hasNext()) {
                    docImage = i.next();
                    // is not already existing??
                    if (!((fileExist(context.getBroker(), existingThumbsCol, docImage, prefix)) || (fileExist(
                            existingThumbsArray, docImage, prefix)))) {
                        if (docImage.getResourceType() == DocumentImpl.BINARY_FILE)
                            // TODO maybe extends for gifs too.
                            if (docImage.getMimeType().startsWith(
                                    "image/jpeg")) {

                                binImage = (BinaryDocument) docImage;

                                // get a byte array representing the image

                                try (final InputStream is = dbbroker.getBinaryResource(transaction, binImage)) {
                                    image = ImageIO.read(is);
                                } catch (IOException ioe) {
                                    throw new XPathException(this, ioe.getMessage());
                                }

                                try {
                                    bImage = ImageModule.createThumb(image, maxThumbHeight,
                                            maxThumbWidth, null);
                                } catch (Exception e) {
                                    throw new XPathException(this, e.getMessage());
                                }

                                if (isSaveToDataBase) {
                                    os = new UnsynchronizedByteArrayOutputStream();
                                    try {
                                        ImageIO.write(bImage, "jpg", os);
                                    } catch (Exception e) {
                                        throw new XPathException(this, e.getMessage());
                                    }
                                    try (final StringInputSource sis = new StringInputSource(os.toByteArray())) {
                                        thumbCollection.storeDocument(
                                                transaction, dbbroker,
                                                XmldbURI.create(prefix
                                                        + docImage.getFileURI()), sis, new MimeType("image/jpeg", MimeType.BINARY));
                                    } catch (final Exception e) {
                                        throw new XPathException(this, e.getMessage());
                                    }
                                    // result.add(new
                                    // StringValue(""+docImage.getFileURI()+"|"+thumbCollection.getURI()+THUMBPREFIX
                                    // + docImage.getFileURI()));
                                } else {
                                    try {
                                        ImageIO
                                                .write(
                                                        bImage,
                                                        "jpg",
                                                        Paths.get(thumbPath
                                                                + "/" + prefix
                                                                + docImage.getFileURI()).toFile());
                                    } catch (Exception e) {
                                        throw new XPathException(this, e.getMessage());
                                    }
                                    // result.add(new StringValue(
                                    // thumbPath.toString() + "/"
                                    // + THUMBPREFIX
                                    // + docImage.getFileURI()));
                                }
                            }
                    } else {

                        // result.add(new StringValue(""+docImage.getURI()+"|"
                        // + ((existingThumbsCol != null) ? ""
                        // + existingThumbsCol.getURI() : thumbDir
                        // .toString()) + "/" + prefix
                        // + docImage.getFileURI()));

                        result.add(new StringValue(this, docImage.getFileURI().toString()));
                    }
                }
            } catch (final PermissionDeniedException | LockException e) {
                throw new XPathException(this, e.getMessage(), e);
            }
            try {
                transact.commit(transaction);
            } catch (Exception e) {
                throw new XPathException(this, e.getMessage());
            }
        }
        final Optional<JournalManager> journalManager = pool.getJournalManager();
        journalManager.ifPresent(j -> j.flush(true, false));
        dbbroker.closeDocument();

		return result;

	}

	private boolean fileExist(DBBroker broker, Collection col, DocumentImpl file, String prefix) throws PermissionDeniedException {
		if (col != null)
			return col.hasDocument(broker, XmldbURI.create(prefix + file.getFileURI()));
		return false;
	}

	private boolean fileExist(List<Path> cols, DocumentImpl file, String prefix) {
		if (cols != null)
            for (Path col : cols) {
                if (FileUtils.fileName(col).endsWith(prefix + file.getFileURI())) {
                    return true;
                }
            }

		return false;
	}
}
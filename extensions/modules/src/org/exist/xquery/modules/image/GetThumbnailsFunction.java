/*
 *  eXist Image Module Extension GetThumbnailsFunction
 *  Copyright (C) 2006-09 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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
package org.exist.xquery.modules.image;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Iterator;

import javax.imageio.ImageIO;

import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
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
			picturePath = new AnyURIValue(picturePath.getStringValue()
					.substring(14));
		}

		AnyURIValue thumbPath = null;
		if (args[1].isEmpty()) {
			thumbPath = new AnyURIValue(picturePath.toXmldbURI().append(
					THUMBPATH));
			isSaveToDataBase = true;
		} else {
			thumbPath = (AnyURIValue) args[1].itemAt(0);
			if (thumbPath.getStringValue().startsWith("file:")) {
				isSaveToDataBase = false;
				thumbPath = new AnyURIValue(thumbPath.getStringValue().substring(5));
			} else {
				isSaveToDataBase = true;
				try {
					XmldbURI thumbsURI = XmldbURI.xmldbUriFor(thumbPath.getStringValue());
					if (!thumbsURI.isAbsolute())
						thumbsURI = picturePath.toXmldbURI().append(thumbPath.toString());
					thumbPath = new AnyURIValue(thumbsURI.toString());
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
			result.add(new StringValue(e.getMessage()));
			return result;
		}

        try(final DBBroker dbbroker = context.getBroker()) {

            // Start transaction
            final TransactionManager transact = pool.getTransactionManager();
            try (final Txn transaction = transact.beginTransaction()) {

                Collection thumbCollection = null;
                File thumbDir = null;
                if (isSaveToDataBase) {
                    try {
                        thumbCollection = dbbroker.getOrCreateCollection(transaction,
                                thumbPath.toXmldbURI());
                        dbbroker.saveCollection(transaction, thumbCollection);
                    } catch (Exception e) {
                        throw new XPathException(this, e.getMessage());
                    }
                } else {
                    thumbDir = new File(thumbPath.toString());
                    if (!thumbDir.isDirectory())
                        try {
                            thumbDir.mkdirs();
                        } catch (Exception e) {
                            throw new XPathException(this, e.getMessage());
                        }

                }

                Collection allPictures = null;
                Collection existingThumbsCol = null;
                File[] existingThumbsArray = null;
                try {
                    allPictures = dbbroker.getCollection(picturePath.toXmldbURI());

                    if (allPictures == null) {
                        return Sequence.EMPTY_SEQUENCE;
                    }

                    if (isSaveToDataBase) {
                        existingThumbsCol = dbbroker.getCollection(thumbPath.toXmldbURI());
                    } else {
                        existingThumbsArray = thumbDir.listFiles(new FilenameFilter() {
                            public boolean accept(File dir, String name) {
                                return (name.endsWith(".jpeg") || name.endsWith(".jpg"));
                            }
                        });
                    }
                } catch (PermissionDeniedException pde) {
                    throw new XPathException(pde.getMessage(), pde);
                }

                DocumentImpl docImage = null;
                BinaryDocument binImage = null;
                @SuppressWarnings("unused")
                BinaryDocument doc = null;
                BufferedImage bImage = null;
                @SuppressWarnings("unused")
                byte[] imgData = null;
                Image image = null;
                ByteArrayOutputStream os = null;

                try {
                    Iterator<DocumentImpl> i = allPictures.iterator(dbbroker);

                    while (i.hasNext()) {
                        docImage = (DocumentImpl) i.next();
                        // is not already existing??
                        if (!((fileExist(context.getBroker(), existingThumbsCol, docImage, prefix)) || (fileExist(
                                existingThumbsArray, docImage, prefix)))) {
                            if (docImage.getResourceType() == DocumentImpl.BINARY_FILE)
                                // TODO maybe extends for gifs too.
                                if (docImage.getMetadata().getMimeType().startsWith(
                                        "image/jpeg")) {

                                    binImage = (BinaryDocument) docImage;

                                    // get a byte array representing the image

                                    try {
                                        InputStream is = dbbroker.getBinaryResource(binImage);
                                        image = ImageIO.read(is);
                                    } catch (IOException ioe) {
                                        throw new XPathException(this, ioe.getMessage());
                                    }

                                    try {
                                        bImage = ImageModule.createThumb(image, maxThumbHeight,
                                                maxThumbWidth);
                                    } catch (Exception e) {
                                        throw new XPathException(this, e.getMessage());
                                    }

                                    if (isSaveToDataBase) {
                                        os = new ByteArrayOutputStream();
                                        try {
                                            ImageIO.write(bImage, "jpg", os);
                                        } catch (Exception e) {
                                            throw new XPathException(this, e.getMessage());
                                        }
                                        try {
                                            doc = thumbCollection.addBinaryResource(
                                                    transaction, dbbroker,
                                                    XmldbURI.create(prefix
                                                            + docImage.getFileURI()), os
                                                            .toByteArray(), "image/jpeg");
                                        } catch (Exception e) {
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
                                                            new File(thumbPath.toString()
                                                                    + "/" + prefix
                                                                    + docImage.getFileURI()));
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

                            result.add(new StringValue(docImage.getFileURI().toString()));
                        }
                    }
                } catch (PermissionDeniedException pde) {
                    throw new XPathException(this, pde.getMessage(), pde);
                }
                try {
                    transact.commit(transaction);
                } catch (Exception e) {
                    throw new XPathException(this, e.getMessage());
                }
            }
            transact.getJournal().flushToLog(true);
            dbbroker.closeDocument();
        }
		return result;

	}

	private boolean fileExist(DBBroker broker, Collection col, DocumentImpl file, String prefix) throws PermissionDeniedException {
		if (col != null)
			return col.hasDocument(broker, XmldbURI.create(prefix + file.getFileURI()));
		return false;
	}

	private boolean fileExist(File[] col, DocumentImpl file, String prefix) {
		if (col != null)
			for (int i = 0; i < col.length; i++) {
				if (col[i].getName().endsWith(prefix + file.getFileURI())) {
					return true;
				}
			}

		return false;
	}
}
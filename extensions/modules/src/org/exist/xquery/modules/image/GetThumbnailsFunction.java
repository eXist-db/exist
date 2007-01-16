package org.exist.xquery.modules.image;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import javax.imageio.ImageIO;

import org.exist.collections.Collection;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
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

	private final static int MAXTHUMBHEIGHT = 100;

	private final static int MAXTHUMBWIDTH = 100;

	private final static String THUMBPREFIX = "";

	private final static String THUMBPATH = "thumbs";

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("thumbnail", ImageModule.NAMESPACE_URI,
					ImageModule.PREFIX),
			"Get or generate thumbnails from the given database collection $a"
					+ "$b point to a location where the thumbnails should be created, $b can be a local path, with the prefix 'xmldb:' a absolute path within the database or with 'rel:' path relative to the given collection at $a."
					+ "You can leave $b empty then the default is 'rel:/thumbs'. $c specifies the dimension of the thumbnails, if empty then the default values are 'maxheight = 100' and 'maxwidth = 100', the first value of $c is 'maxheight' and the second 'maxwidth'. $d is the prefix (if any) for the thumbnails",
			new SequenceType[] {
					new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE),
					new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE) },

			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));

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
					throw new XPathException(getASTNode(), e.getMessage());
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

		DBBroker dbbroker = context.getBroker();

		BrokerPool pool = null;
		try {
			pool = BrokerPool.getInstance();
		} catch (Exception e) {
			result.add(new StringValue(e.getMessage()));
			return result;
		}
		TransactionManager transact = pool.getTransactionManager();

		// Start transaction
		Txn transaction = transact.beginTransaction();

		Collection thumbCollection = null;
		File thumbDir = null;
		if (isSaveToDataBase) {
			try {
				thumbCollection = dbbroker.getOrCreateCollection(transaction,
						thumbPath.toXmldbURI());
				dbbroker.saveCollection(transaction, thumbCollection);
			} catch (Exception e) {
				throw new XPathException(e.getMessage());
			}
		} else {
			thumbDir = new File(thumbPath.toString());
			if (!thumbDir.isDirectory())
				try {
					thumbDir.mkdirs();
				} catch (Exception e) {
					throw new XPathException(e.getMessage());
				}

		}

		Collection allPictures = dbbroker.getCollection(picturePath
				.toXmldbURI());

		if (allPictures == null) {
			return Sequence.EMPTY_SEQUENCE;
		}

		Collection existingThumbsCol = null;
		File[] existingThumbsArray = null;
		if (isSaveToDataBase) {
			existingThumbsCol = dbbroker.getCollection(thumbPath.toXmldbURI());
		} else {
			existingThumbsArray = thumbDir.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return (name.endsWith(".jpeg") || name.endsWith(".jpg"));
				}
			});
		}

		DocumentImpl docImage = null;
		BinaryDocument binImage = null;
		BinaryDocument doc = null;
		BufferedImage bImage = null;
		byte[] imgData = null;
		Image image = null;
		ByteArrayOutputStream os = null;

		Iterator i = allPictures.iterator(dbbroker);

		while (i.hasNext()) {
			docImage = (DocumentImpl) i.next();
			// is not already existing??
			if (!((fileExist(existingThumbsCol, docImage, prefix)) || (fileExist(
					existingThumbsArray, docImage, prefix)))) {
				if (docImage.getResourceType() == DocumentImpl.BINARY_FILE)
					// TODO maybe extends for gifs too.
					if (docImage.getMetadata().getMimeType().startsWith(
							"image/jpeg")) {

						binImage = (BinaryDocument) docImage;

						// get a byte array representing the image
						imgData = dbbroker.getBinaryResource(binImage);

						try {
							image = ImageIO.read(new ByteArrayInputStream(
									imgData));
						} catch (IOException ioe) {
							throw new XPathException(ioe.getMessage());
						}

						try {
							bImage = ImageModule.createThumb(image, maxThumbHeight,
									maxThumbWidth);
						} catch (Exception e) {
							throw new XPathException(e.getMessage());
						}

						if (isSaveToDataBase) {
							os = new ByteArrayOutputStream();
							try {
								ImageIO.write(bImage, "jpg", os);
							} catch (Exception e) {
								throw new XPathException(e.getMessage());
							}
							try {
								doc = thumbCollection.addBinaryResource(
										transaction, dbbroker,
										XmldbURI.create(prefix
												+ docImage.getFileURI()), os
												.toByteArray(), "image/jpeg");
							} catch (Exception e) {
								throw new XPathException(e.getMessage());
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
								throw new XPathException(e.getMessage());
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

		try {
			transact.commit(transaction);
		} catch (Exception e) {
			throw new XPathException(e.getMessage());
		}
		transact.getJournal().flushToLog(true);
		dbbroker.closeDocument();

		return result;

	}

	private boolean fileExist(Collection col, DocumentImpl file, String prefix) {
		if (col != null)
			return col.hasDocument(XmldbURI.create(prefix + file.getFileURI()));
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
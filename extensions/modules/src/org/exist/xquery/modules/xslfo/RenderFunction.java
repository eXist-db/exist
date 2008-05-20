/*
 *  eXist Apache FOP Transformation Extension
 *  Copyright (C) 2007 Craig Goodyer at the University of the West of England
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

package org.exist.xquery.modules.xslfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.TransformerHandler;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xslt.TransformerFactoryAllocator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Craig Goodyer <craiggoodyer@gmail.com>
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class RenderFunction extends BasicFunction {
	public final static FunctionSignature signature = new FunctionSignature(
			new QName("render", XSLFOModule.NAMESPACE_URI, XSLFOModule.PREFIX),
			"Renders a given XSL-FO document. $a is the XSL-FO node, $b is the required mime-type, $c is parameters to the transformation. "
					+ "Returns an xs:base64binary of the result."
					+ "Parameters are specified with the structure: "
					+ "<parameters><param name=\"param-name1\" value=\"param-value1\"/>"
					+ "</parameters>. "
					+ "Recognised rendering parameters are: author, title, keywords, dpi and user-config.",
			new SequenceType[] {
					new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE));

	/**
	 * Constructor for RenderFunction, which returns a new instance of this
	 * class.
	 * 
	 * @param context
	 * @param signature
	 */
	public RenderFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/*
	 * Actual implementation of the rendering process. When a function in this
	 * module is called, this method is executed with the given inputs. @param
	 * Sequence[] args (XSL-FO, mime-type, parameters) @param Sequence
	 * contextSequence (default sequence)
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// gather input XSL-FO document
		// if no input document (empty), return empty result as we need data to
		// process
		if (args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		Item inputNode = args[0].itemAt(0);

		// get mime-type
		String mimeType = args[1].getStringValue();

		// get parameters
		Properties parameters = new Properties();
		if (!args[2].isEmpty()) {
			parameters = ModuleUtils.parseParameters(((NodeValue) args[2]
					.itemAt(0)).getNode());

		}

		try {
			// setup a transformer handler
			TransformerHandler handler = TransformerFactoryAllocator
					.getTransformerFactory(context.getBroker())
					.newTransformerHandler();
			Transformer transformer = handler.getTransformer();

			// set the parameters if any
			if (parameters.size() > 0) {
				Enumeration keys = parameters.keys();
				while (keys.hasMoreElements()) {
					String name = (String) keys.nextElement();
					String value = parameters.getProperty(name);
					transformer.setParameter(name, value);
				}
			}

			// setup the FopFactory
			FopFactory fopFactory = FopFactory.newInstance();
			String userConfig = parameters.getProperty("user-config");
			if (userConfig != null) {
				DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
				Configuration cfg = cfgBuilder.buildFromFile(new File(
						userConfig));
				fopFactory.setUserConfig(cfg);
			}

			// setup the foUserAgent, using given parameters held in the
			// transformer handler
			FOUserAgent foUserAgent = setupFOUserAgent(fopFactory
					.newFOUserAgent(), parameters, transformer);

			// create new instance of FOP using the mimetype, the created user
			// agent, and the output stream
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Fop fop = fopFactory.newFop(mimeType, foUserAgent, baos);

			// Obtain FOP's DefaultHandler
			DefaultHandler dh = fop.getDefaultHandler();

			// process the XSL-FO
			dh.startDocument();
			inputNode.toSAX(context.getBroker(), dh, new Properties());
			dh.endDocument();

			// return the result
			return new Base64Binary(baos.toByteArray());
		} catch (TransformerException te) {
			throw new XPathException(te);
		} catch (IOException ioe) {
			throw new XPathException(ioe);
		} catch (ConfigurationException ce) {
			throw new XPathException(ce);
		} catch (SAXException se) {
			throw new XPathException(se);
		}
	}

	/**
	 * Setup the UserAgent for FOP, from given parameters *
	 * 
	 * @param transformer
	 *            Created based on the XSLT, so containing any parameters to the
	 *            XSL-FO specified in the XQuery
	 * @param parameters
	 *            any user defined parameters to the XSL-FO process
	 * @return FOUserAgent The generated FOUserAgent to include any parameters
	 *         passed in
	 */
	private FOUserAgent setupFOUserAgent(FOUserAgent foUserAgent,
			Properties parameters, Transformer transformer)
			throws TransformerException {

		// setup the foUserAgent as per the parameters given
		foUserAgent.setProducer("eXist with Apache FOP");

		if (transformer.getParameter("FOPauthor") != null)
			foUserAgent.setAuthor(parameters.getProperty("author"));

		if (transformer.getParameter("FOPtitle") != null)
			foUserAgent.setTitle(parameters.getProperty("title"));

		if (transformer.getParameter("FOPkeywords") != null)
			foUserAgent.setTitle(parameters.getProperty("keywords"));

		if (transformer.getParameter("FOPdpi") != null) {
			String dpiStr = (String) transformer.getParameter("dpi");
			try {
				foUserAgent.setTargetResolution(Integer.parseInt(dpiStr));
			} catch (NumberFormatException nfe) {
				throw new TransformerException(
						"Cannot parse value of \"dpi\" - " + dpiStr
								+ " to configure FOUserAgent");
			}
		}

		return foUserAgent;
	}
}
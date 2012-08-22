package org.exist.xquery.modules.xslfo;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Adapter for Antenna House XSL Formatter 5.0
 */
public class AntennaHouseProcessorAdapter implements ProcessorAdapter {

    private final static Logger LOG = Logger.getLogger(AntennaHouseProcessorAdapter.class);

    protected Class formatter;
    protected Method renderMethod;
    protected OutputStream out;
    protected File xmlFile;
    protected FileWriter xmlWriter;

    @Override
    public ContentHandler getContentHandler(DBBroker broker, NodeValue configFile, Properties parameters, String mimeType, OutputStream os) throws XPathException, SAXException {
        this.out = os;
        try {
            LOG.info("Initializing AntennaHouse Formatter");
            formatter = Class.forName("jp.co.antenna.XfoJavaCtl.XfoObj");
            renderMethod = formatter.getMethod("render", InputStream.class, OutputStream.class, String.class);

            xmlFile = File.createTempFile("xslfo", ".xml");
            xmlWriter = new FileWriter(xmlFile);
            Properties properties = new Properties();
            properties.setProperty(OutputKeys.INDENT, "no");
            FOContentHandler sax = new FOContentHandler(xmlWriter, properties);
            return sax;
        } catch (ClassNotFoundException e) {
            throw new SAXException("Antenna Java API not found", e);
        } catch (NoSuchMethodException e) {
            throw new SAXException("Antenna Java API method not found", e);
        } catch (IOException e) {
            throw new SAXException("IO error caught while rendering FO");
        }
    }

    @Override
    public void cleanup() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private class FOContentHandler extends SAXSerializer {

        public FOContentHandler(Writer writer, Properties outputProperties) {
            super(writer, outputProperties);
        }

        @Override
        public void startDocument() throws SAXException {
            super.startDocument();
            LOG.info("Started document");
        }

        @Override
        public void endDocument() throws SAXException {
            super.endDocument();
            LOG.info("Transforming fo input: " + xmlFile.getAbsolutePath());

            try {
                xmlWriter.close();
                FileInputStream is = new FileInputStream(xmlFile);

                Object formatterObj = formatter.newInstance();

                renderMethod.invoke(formatterObj, is, out, "@PDF");
            } catch (Exception e) {
                throw new SAXException(e.getMessage(), e);
            }
        }
    }
}

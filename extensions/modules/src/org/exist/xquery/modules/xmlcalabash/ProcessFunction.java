package org.exist.xquery.modules.xmlcalabash;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.SAXAdapter;
import org.exist.memtree.DocumentImpl;
import org.exist.Namespaces;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcConfiguration;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.util.S9apiUtils;
import com.xmlcalabash.util.RelevantNodes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.SAXException;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.Serializer;

import javax.xml.transform.sax.SAXSource;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;

import com.xmlcalabash.runtime.XPipeline;

public class ProcessFunction extends BasicFunction {

    @SuppressWarnings("unused")
	private final static Logger logger = Logger.getLogger(ProcessFunction.class);

    private XProcRuntime runtime = null;

    String outputResult;
    
    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("process", XMLCalabashModule.NAMESPACE_URI, XMLCalabashModule.PREFIX),
			"Function which invokes xmlcalabash XProc processor.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("pipeline", Type.STRING, Cardinality.EXACTLY_ONE, "XProc Pipeline"),
                    new FunctionParameterSequenceType("output", Type.STRING, Cardinality.EXACTLY_ONE, "Output result")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "return type"));

	public ProcessFunction(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {

        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        String pipelineURI = args[0].getStringValue();
        String outputURI = args[1].getStringValue();

       try {

        String[] calabash_args = {"-oresult="+outputURI,pipelineURI};

        PrintStream stdout = System.out;

        com.xmlcalabash.drivers.Main main = new com.xmlcalabash.drivers.Main();

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(byteStream, true));
        main.run(calabash_args);
        outputResult = byteStream.toString();
        System.setOut(stdout);

       } catch (Exception e) {
           System.err.println(e);
       }

        StringReader reader = new StringReader(outputResult);
                try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(reader);

            XMLReader xr = null;

            if (xr == null) {
                SAXParser parser = factory.newSAXParser();
                xr = parser.getXMLReader();
            }

            SAXAdapter adapter = new SAXAdapter(context);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);

            return (DocumentImpl) adapter.getDocument();
        } catch (ParserConfigurationException e) {
            throw new XPathException(this, "Error while constructing XML parser: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(this, "Error while parsing XML: " + e.getMessage(), e);
        }

	}
}

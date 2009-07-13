package org.exist.xquery.functions.system;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.storage.SystemTask;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 
 */
public class TriggerSystemTask extends BasicFunction {

	protected final static Logger logger = Logger.getLogger(TriggerSystemTask.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("trigger-system-task", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Trigger a system task. The first argument specifies the name of the Java class to be executed. The " +
            "class has to implement org.exist.storage.SystemTask. An XML fragment may be passed as second " +
            "argument. It should have the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/>" +
            "</parameters>. The parameters are transformed into Java properties and passed to the system task.",
			new SequenceType[]{
                new FunctionParameterSequenceType("java-classname", Type.STRING, Cardinality.EXACTLY_ONE, "The full name of the class to execute.  It must implement org.exist.storage.SystemTask"),
                new FunctionParameterSequenceType("task-parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>")
            },
			new SequenceType(Type.ITEM, Cardinality.EMPTY));


    public TriggerSystemTask(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		logger.info("Entering " + SystemModule.PREFIX + ":trigger-system-task");
        String className = args[0].getStringValue();
        Properties properties = new Properties();
        if (args[1].hasOne()) {
            parseParameters(((NodeValue) args[1].itemAt(0)).getNode(), properties);
        }
        try {
            Class clazz = Class.forName(className);
            Object taskObject = clazz.newInstance();
            if (!(taskObject instanceof SystemTask)) {
                XPathException xPathException = new XPathException(this, className + " is not an instance of org.exist.storage.SystemTask");
                logger.error("Java classname is not a SystemTask", xPathException);
				throw xPathException;
            }
            SystemTask task = (SystemTask) taskObject;
            task.configure(context.getBroker().getConfiguration(), properties);
            LOG.info("Triggering SystemTask: " + className);
            context.getBroker().getBrokerPool().triggerSystemTask(task);
        }
        catch (ClassNotFoundException e) {
            String message = "system task class '" + className + "' not found";
            logger.error(message, e);
			throw new XPathException(this, message);
        }
        catch (InstantiationException e) {
            String message = "system task '" + className + "' can not be instantiated";
            logger.error(message, e);
			throw new XPathException(this, message);
        }
        catch (IllegalAccessException e) {
            String message = "system task '" + className + "' can not be accessed";
            logger.error(message, e);
			throw new XPathException(this, message);
        } catch (EXistException e) {
            String message = "system task " + className + " reported an error during initialization: ";
            logger.error(message, e);
			throw new XPathException(this, message + e.getMessage(), e);
        }
		logger.info("Exiting " + SystemModule.PREFIX + ":trigger-system-task");
        return Sequence.EMPTY_SEQUENCE;
    }

    private void parseParameters(Node options, Properties properties) throws XPathException {
		if(options.getNodeType() == Node.ELEMENT_NODE && options.getLocalName().equals("parameters")) {
			Node child = options.getFirstChild();
			while(child != null) {
				if(child.getNodeType() == Node.ELEMENT_NODE && child.getLocalName().equals("param")) {
					Element elem = (Element)child;
					String name = elem.getAttribute("name");
					String value = elem.getAttribute("value");
					logger.trace("parseParameters: name[" + name + "] value[" + value + "]");
					if(name == null || value == null)
						throw new XPathException(this, "Name or value attribute missing for stylesheet parameter");
					properties.setProperty(name, value);
				}
				child = child.getNextSibling();
			}
		}
	}
}

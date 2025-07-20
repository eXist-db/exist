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
package org.exist.xquery.functions.system;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

	protected final static Logger logger = LogManager.getLogger(TriggerSystemTask.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("trigger-system-task", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Trigger a system task.",
			new SequenceType[]{
                new FunctionParameterSequenceType("java-classname", Type.STRING, Cardinality.EXACTLY_ONE, "The full name of the Java class to execute.  It must implement org.exist.storage.SystemTask"),
                new FunctionParameterSequenceType("task-parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "The XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>")
            },
			new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE));


    public TriggerSystemTask(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final String className = args[0].getStringValue();
        final Properties properties = new Properties();
        if (args[1].hasOne()) {
            parseParameters(((NodeValue) args[1].itemAt(0)).getNode(), properties);
        }
        
        try {
            final Class<?> clazz = Class.forName(className);
            final Object taskObject = clazz.newInstance();

            if (!(taskObject instanceof SystemTask task)) {
                final XPathException xPathException = new XPathException(this, className + " is not an instance of org.exist.storage.SystemTask");
                logger.error("Java classname is not a SystemTask", xPathException);
				throw xPathException;
            }

            task.configure(context.getBroker().getConfiguration(), properties);
            LOG.info("Triggering SystemTask: {}", className);
            context.getBroker().getBrokerPool().triggerSystemTask(task);

        } catch (final ClassNotFoundException e) {
            final String message = "system task class '" + className + "' not found";
            logger.error(message, e);
			throw new XPathException(this, message);

        } catch (final InstantiationException e) {
            final String message = "system task '" + className + "' can not be instantiated";
            logger.error(message, e);
			throw new XPathException(this, message);

        } catch (final IllegalAccessException e) {
            final String message = "system task '" + className + "' can not be accessed";
            logger.error(message, e);
			throw new XPathException(this, message);

        } catch (final EXistException e) {
            final String message = "system task " + className + " reported an error during initialization: ";
            logger.error(message, e);
			throw new XPathException(this, message + e.getMessage(), e);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private void parseParameters(Node options, Properties properties) throws XPathException {
		if(options.getNodeType() == Node.ELEMENT_NODE && "parameters".equals(options.getLocalName())) {
			Node child = options.getFirstChild();
			while(child != null) {
				if(child.getNodeType() == Node.ELEMENT_NODE && "param".equals(child.getLocalName())) {
					final Element elem = (Element)child;
					final String name = elem.getAttribute("name");
					final String value = elem.getAttribute("value");
                    logger.trace("parseParameters: name[{}] value[{}]", name, value);
					if (name.isEmpty() || value.isEmpty()) {
                        // TODO: add error code
                        throw new XPathException(this, "Name or value attribute missing for stylesheet parameter");
                    }
					properties.setProperty(name, value);
				}
				child = child.getNextSibling();
			}
		}
	}
}

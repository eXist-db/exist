/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.config.mapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.function.Supplier;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.NewClass;

import static java.lang.invoke.MethodType.methodType;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class Constructor {

    private static Map<Object, Configuration> configurations = new HashMap<>();

    public static Configuration getConfiguration(final Object obj) {
        return configurations.get(obj);
    }

    /**
     * Create new java object by mapping instructions.
     * @param newClazz object
     * @param instance to load
     * @param conf configuration
     * @return new java object by mapping instructions.
     */
    public static Object load(final NewClass newClazz,
            final Configurable instance, final Configuration conf) {

        final String url = newClazz.mapper();
        if (url == null) {
            Configurator.LOG.error("Field must have 'ConfigurationFieldClassMask' annotation or " +
                    "registered mapping instruction for class '" + newClazz.name() + "' [" + conf.getName() + "], " +
                    "skip instance creation.");
            return null;
        }

        try (final InputStream is = instance.getClass().getClassLoader().getResourceAsStream(url)) {
            if (is == null) {
                Configurator.LOG.error("Registered mapping instruction for class '" + newClazz.name() + "' " +
                        "missing resource '" + url + "', skip instance creation.");
                return null;
            }

            final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            try {
                final XMLStreamReader reader = inputFactory.createXMLStreamReader(is);

            Object obj = null;
            final Deque<Object> objs = new ArrayDeque<>();
            final Deque<CallMethod> instructions = new ArrayDeque<>();

                int eventType;
                while (reader.hasNext()) {
                    eventType = reader.next();

                    switch (eventType) {
                        case XMLEvent.START_ELEMENT:
                            String localName = reader.getLocalName();

                            if ("class".equals(localName)) {
                                if (!"name".equals(reader.getAttributeLocalName(0))) {
                                    Configurator.LOG.error("class element first attribute must be 'name', skip instance creation.");
                                    return null;
                                }

                            final String clazzName = reader.getAttributeValue(0);
                            final Class<?> clazz = Class.forName(clazzName);
                            final MethodHandles.Lookup lookup = MethodHandles.lookup();
                            final MethodHandle methodHandle = lookup.findConstructor(clazz, methodType(void.class));
                            final Supplier<Object> constructor =
                                    (Supplier<Object>)
                                            LambdaMetafactory.metafactory(
                                                    lookup, "get", methodType(Supplier.class),
                                                    methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

                            final Object newInstance = constructor.get();
                            if (obj == null) {
                                obj = newInstance;
                            }
                            objs.push(newInstance);

                            if (!instructions.isEmpty()) {
                                instructions.peek().setValue(newInstance);
                            }

                            } else if ("callMethod".equals(localName)) {

                            Configuration _conf_ = conf;
                            if (!instructions.isEmpty()) {
                                _conf_ = instructions.peek().getConfiguration();
                            }

                                final CallMethod call = new CallMethod(objs.peek(), _conf_);

                                for (int i = 0; i < reader.getAttributeCount(); i++) {
                                    call.set(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                                }

                            instructions.push(call);
                        }
                        break;
                    case XMLEvent.END_ELEMENT:
                        localName = reader.getLocalName();
                        //System.out.println("END_ELEMENT "+localName);

                            if ("class".equals(localName)) {
                                objs.pop();
                            } else if ("callMethod".equals(localName)) {
                                final CallMethod call = instructions.pop();
                                call.eval();
                            }

                            break;
                    }
                }

                configurations.put(obj, conf);
                return obj;

            } catch (final Throwable e) {
                if (e instanceof InterruptedException) {
                    // NOTE: must set interrupted flag
                    Thread.currentThread().interrupt();
                }

                Configurator.LOG.error(e);
            }
            return null;
        } catch (final IOException e) {
            Configurator.LOG.error("Registered mapping instruction for class '" + newClazz.name() + "' " +
                    "missing resource '" + url + "', skip instance creation.");
            return null;
        }
    }
}

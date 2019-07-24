/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2018 The eXist Project
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
package org.exist.storage;

import java.lang.invoke.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.lazy.LazyValE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.util.Configuration;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;
import static java.lang.invoke.MethodType.methodType;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class BrokerFactory {

    private static final Logger LOG = LogManager.getLogger(BrokerFactory.class);
    private static final ConcurrentMap<String, LazyValE<BiFunction<BrokerPool, Configuration, DBBroker>, RuntimeException>> CONSTRUCTORS = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public static final String PROPERTY_DATABASE = "database";

    public static void plug(final String brokerId, final Class<? extends DBBroker> clazz) {
        CONSTRUCTORS.computeIfAbsent(formatBrokerId(brokerId), key -> new LazyValE<>(() -> getConstructor(key, clazz)));
    }

    static {
        plug(formatBrokerId("NATIVE"), NativeBroker.class);
    }

    /**
     * Constructs a DBBroker instance.
     *
     * @param brokerPool the database's broker pool.
     * @param configuration the database's configuration.
     *
     * @return DBBroker an instance of a sub-class of {@link DBBroker}.
     *
     * @throws EXistException in case of an eXist-db error
     * @throws RuntimeException if the database backend cannot be constructed.
     */
    public static DBBroker getInstance(final BrokerPool brokerPool, final Configuration configuration) throws RuntimeException, EXistException {
        final long start = System.currentTimeMillis();

        final String brokerId = getBrokerId(configuration);
        final LazyValE<BiFunction<BrokerPool, Configuration, DBBroker>, RuntimeException> constructor
                = CONSTRUCTORS.get(brokerId);
        if (constructor == null) {
            throw new IllegalStateException("No database backend found for: " + brokerId);
        }

        final DBBroker broker = constructor.get().apply(brokerPool, configuration);

        if (LOG.isTraceEnabled()) {
            final long end = System.currentTimeMillis();
            LOG.trace("Constructed DBBroker in: " + (end - start) + " ms");
        }

        return broker;
    }

    /**
     * Creates a constructor function for a sub-class of DBBroker.
     *
     * @param brokerId the id of the DBBroker.
     * @param clazz the sub-class of DBBroker.
     *
     * @return Either a constructor function, or a RuntimeException.
     */
    @SuppressWarnings("unchecked")
    private static Either<RuntimeException, BiFunction<BrokerPool, Configuration, DBBroker>> getConstructor(final String brokerId, final Class<? extends DBBroker> clazz) {
        try {
            final MethodHandle methodHandle = LOOKUP.findConstructor(clazz, methodType(void.class, BrokerPool.class, Configuration.class));

            // see https://stackoverflow.com/questions/50211216/how-to-invoke-constructor-using-lambdametafactory#50211536
            return Right((BiFunction<BrokerPool, Configuration, DBBroker>) LambdaMetafactory.metafactory(
                    LOOKUP,
                    "apply",
                    methodType(BiFunction.class),
                    methodHandle.type().erase(), methodHandle, methodHandle.type()
            ).getTarget().invokeExact());

        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            return Left(new RuntimeException("Can't get database backend: " + brokerId, e));
        }
    }

    /**
     * Gets the Broker ID from a Configuration.
     *
     * @param configuration the configuration.
     * @return the broker ID.
     *
     * @throws IllegalArgumentException if the configuration does not define a broker ID.
     */
    private static String getBrokerId(final Configuration configuration) throws IllegalArgumentException {
        final String brokerId = (String) configuration.getProperty(PROPERTY_DATABASE);
        if (brokerId == null) {
            throw new IllegalArgumentException("No database defined in: " + configuration.getConfigFilePath());
        }

        return formatBrokerId(brokerId);
    }

    /**
     * Ensures consistent formatting fo the Broker ID.
     *
     * Repair name {@see https://sourceforge.net/p/exist/bugs/810/}.
     *
     * @param brokerId the broker id to be formatted.
     *
     * @return consistently formatted broker id.
     */
    private static String formatBrokerId(final String brokerId) {
        return brokerId.toUpperCase(Locale.ENGLISH);
    }
}

package org.exist.xquery.value;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public abstract class BinaryValueType<T extends FilterOutputStream> {

    private final int xqueryType;
    private final Class<T> encoder;

    public BinaryValueType(int xqueryType, Class<T> encoder) {
        this.xqueryType = xqueryType;
        this.encoder = encoder;
    }

    public int getXQueryType() {
        return xqueryType;
    }

    public T getEncoder(OutputStream os) throws IOException {
        try {
            Constructor<T> c = encoder.getConstructor(OutputStream.class);
            T fos = c.newInstance(os);
            return fos;
        } catch(NoSuchMethodException nsme) {
            throw new IOException("Unable to get binary encoder '" + encoder.getName() +  "': " + nsme.getMessage(), nsme);
        } catch(InstantiationException ie) {
            throw new IOException("Unable to get binary encoder '" + encoder.getName() +  "': " + ie.getMessage(), ie);
        } catch(IllegalArgumentException iae) {
            throw new IOException("Unable to get binary encoder '" + encoder.getName() +  "': " + iae.getMessage(), iae);
        } catch(IllegalAccessException iae) {
            throw new IOException("Unable to get binary encoder '" + encoder.getName() +  "': " + iae.getMessage(), iae);
        } catch(InvocationTargetException ite) {
            throw new IOException("Unable to get binary encoder '" + encoder.getName() +  "': " + ite.getMessage(), ite);
        }
    }
}

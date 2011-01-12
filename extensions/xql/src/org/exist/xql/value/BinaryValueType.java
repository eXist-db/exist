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
    private final Class<T> coder;

    public BinaryValueType(int xqueryType, Class<T> coder) {
        this.xqueryType = xqueryType;
        this.coder = coder;
    }

    public int getXQueryType() {
        return xqueryType;
    }

    public T getEncoder(OutputStream os) throws IOException {
        return instantiateCoder(os, true);
    }
    
    public T getDecoder(OutputStream os) throws IOException {
        return instantiateCoder(os, false);
    }
    
    private T instantiateCoder(OutputStream stream, boolean encoder) throws IOException {
        try {
            Constructor<T> c = coder.getConstructor(OutputStream.class, boolean.class);
            T f = c.newInstance(stream, encoder);
            return f;
        } catch(NoSuchMethodException nsme) {
            throw new IOException("Unable to get binary coder '" + coder.getName() +  "': " + nsme.getMessage(), nsme);
        } catch(InstantiationException ie) {
            throw new IOException("Unable to get binary coder '" + coder.getName() +  "': " + ie.getMessage(), ie);
        } catch(IllegalArgumentException iae) {
            throw new IOException("Unable to get binary coder '" + coder.getName() +  "': " + iae.getMessage(), iae);
        } catch(IllegalAccessException iae) {
            throw new IOException("Unable to get binary coder '" + coder.getName() +  "': " + iae.getMessage(), iae);
        } catch(InvocationTargetException ite) {
            throw new IOException("Unable to get binary coder '" + coder.getName() +  "': " + ite.getMessage(), ite);
        }
    }
}
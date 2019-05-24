package io.quarkus.router;

import java.io.Closeable;

/**
 * A representation of a virtual channel. This can
 */
public interface RouterRegistration extends Closeable {

    /**
     * The name of the virtual channel. Used for debugging purposes
     *
     * @return The name
     */
    String getName();

    /**
     * close the virtual channel. This will de-register it from all registered routes
     */
    void close();

}

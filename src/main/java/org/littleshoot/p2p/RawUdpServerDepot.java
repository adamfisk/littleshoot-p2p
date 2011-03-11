package org.littleshoot.p2p;

import java.net.Socket;
import java.util.Collection;

import org.json.simple.JSONObject;

/**
 * Interface for accessing sessions/sockets in the class for keeping track
 * of such things.
 */
public interface RawUdpServerDepot {

    /**
     * Accessor for the {@link Socket} with the specified ID.
     * 
     * @param id The ID of the socket we're looking for.
     * @return The {@link Socket}.
     */
    Socket getSocket(String id);

    /**
     * Get all IDs of current sessions.
     * 
     * @return All IDs of current sessions.
     */
    Collection<String> getIds();

    /**
     * Adds the specified socket to the depot.
     * 
     * @param id The ID for the socket.
     * @param sock The {@link Socket} to add.
     */
    void addSocket(String id, Socket sock);

    /**
     * Adds an error.
     * 
     * @param id The ID for the session.
     * @param msg The error message.
     */
    void addError(String id, String msg);

    JSONObject toJson();
}

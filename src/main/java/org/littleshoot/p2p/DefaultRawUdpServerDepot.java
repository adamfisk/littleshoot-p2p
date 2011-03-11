package org.littleshoot.p2p;

import java.io.IOException;
import java.net.Socket;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.littleshoot.util.SessionSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class accepts incoming, typically relayed raw UDP data connections.
 */
public class DefaultRawUdpServerDepot implements SessionSocketListener, 
    RawUdpServerDepot {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final Map<String, TimestampedSocket> sessions = 
        new ConcurrentHashMap<String, TimestampedSocket>();
    
    public DefaultRawUdpServerDepot() {
        startPurging();
        startServerThreaded();
    }
    
    private void startServerThreaded() {
        final Runnable runner = new Runnable() {
            public void run() {
                //startServer();
            }
        };
        final Thread t = new Thread(runner, "Incoming-Raw-UDP-Server-Thread");
        t.setDaemon(true);
        t.start();
    }

    private void startPurging() {
        final Timer t = new Timer();
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                final long now = System.currentTimeMillis();
                synchronized (sessions) {
                    for (final Entry<String, TimestampedSocket> e : sessions.entrySet()) {
                        final TimestampedSocket ts = e.getValue();
                        if (now - ts.time < 30 * 1000) {
                            // Ignore new sockets.
                            log.info("Not purging now socket");
                            return;
                        }
                        final Socket sock = ts.sock;
                        if (!sock.isConnected()) {
                            log.info("Removing unconnected socket!");
                            IOUtils.closeQuietly(sock);
                            sessions.remove(e.getKey());
                        }
                        
                    }
                }
            }
        };
        t.schedule(task, 30 * 1000, 30 * 1000);
    }

    public void onSocket(final String id, final Socket sock) throws IOException{
        addSocket(id, sock);
    }
    

    public void addSocket(final String id, final Socket sock) {
        log.info("Adding socket!!");
        sessions.put(id, new TimestampedSocket(sock));
    }
    
    private static final class TimestampedSocket {

        private final long time = System.currentTimeMillis();
        private final Socket sock;

        public TimestampedSocket(final Socket sock) {
            this.sock = sock;
        }
    }

    public Socket getSocket(final String id) {
        final TimestampedSocket ts = sessions.get(id);
        if (ts == null) return null;
        return ts.sock;
    }

    public Collection<String> getIds() {
        synchronized (sessions) {
            return sessions.keySet();
        }
    }

    public void addError(final String id, final String msg) {
        log.info("Adding error");
    }

    public JSONObject toJson() {
        final JSONObject json = new JSONObject();
        final JSONArray incoming = new JSONArray();
        final Collection<String> ids = getIds();
        log.info("Building JSON from IDs: {}", ids);
        for (final String id : ids) {
            incoming.add(id);
        }
        json.put("incoming", incoming);
        return json;
    }
}

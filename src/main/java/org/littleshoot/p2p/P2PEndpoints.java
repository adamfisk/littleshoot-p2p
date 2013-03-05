package org.littleshoot.p2p;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.lang.StringUtils;
import org.lastbamboo.common.ice.EndpointFactory;
import org.lastbamboo.common.ice.IceMediaStreamFactory;
import org.lastbamboo.common.ice.IceMediaStreamFactoryImpl;
import org.lastbamboo.common.ice.IceOfferAnswerFactory;
import org.lastbamboo.common.ice.MappedServerSocket;
import org.lastbamboo.common.ice.MappedTcpAnswererServer;
import org.lastbamboo.common.ice.MappedTcpOffererServerPool;
import org.lastbamboo.common.ice.UdpSocketFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.p2p.P2PClient;
import org.lastbamboo.common.portmapping.NatPmpService;
import org.lastbamboo.common.portmapping.PortMapListener;
import org.lastbamboo.common.portmapping.PortMappingProtocol;
import org.lastbamboo.common.portmapping.UpnpService;
import org.lastbamboo.common.stun.client.PublicIpAddress;
import org.lastbamboo.common.stun.client.StunServerRepository;
import org.lastbamboo.common.turn.client.TurnClientListener;
import org.lastbamboo.common.turn.http.server.ServerDataFeeder;
import org.littleshoot.commom.xmpp.ControlXmppP2PClient;
import org.littleshoot.commom.xmpp.DefaultXmppUriFactory;
import org.littleshoot.commom.xmpp.XmppP2PClient;
import org.littleshoot.commom.xmpp.XmppProtocolSocketFactory;
import org.littleshoot.util.CandidateProvider;
import org.littleshoot.util.CommonUtils;
import org.littleshoot.util.DnsSrvCandidateProvider;
import org.littleshoot.util.FiveTuple;
import org.littleshoot.util.SessionSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.barchart.udt.ResourceUDT;

/**
 * Class that builds all the elements of the LittleShoot P2P platform.
 */
public class P2PEndpoints {
    
    /**
     * The logger for this class.
     */
    private final static Logger log = LoggerFactory.getLogger(P2PEndpoints.class);
    
    /**
     * Note that in cases where we're not using a relay, this is relay just
     * the time to wait for the P2P socket to resolve, in seconds.
     */
    private static final int DEFAULT_RELAY_WAIT_TIME = 30;
    
    static {
        // Use Google Public DNS.
        System.setProperty("sun.net.spi.nameservice.nameservers", 
            "8.8.8.8,8.8.4.4");
        
        // We need to set the System property for Barchart UDT to extract
        // its libraries to a place where we always have permission to write 
        // to.
        System.setProperty(ResourceUDT.PROPERTY_LIBRARY_EXTRACT_LOCATION, 
            CommonUtils.getLittleShootDir().getAbsolutePath());
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static XmppP2PClient newXmppP2PHttpClient(
        final InetSocketAddress serverAddress) throws IOException {
        return newXmppP2PHttpClient("shoot", emptyNatPmpService(), 
            emptyUpnpService(), serverAddress);
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static P2PClient newXmppP2PHttpClient(final String protocol,
        final InetSocketAddress serverAddress) throws IOException {
        return newXmppP2PHttpClient(protocol, emptyNatPmpService(), 
            emptyUpnpService(), serverAddress);
    }
    
    public static XmppP2PClient newXmppP2PHttpClient(
        final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress) 
        throws IOException {
        return newXmppP2PHttpClient(protocol, natPmpService,
            upnpService, serverAddress, 
            (SSLSocketFactory) SSLSocketFactory.getDefault(),
            ServerSocketFactory.getDefault(), serverAddress, true);
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @param natPmpService The NAT PMP implementation.
     * @param upnpService The UPnP implementation.
     * @param socketFactory The factory for creating plain TCP sockets. This
     * could be an SSL socket factory, for example, to create SSL connections
     * to peers when connecting over TCP.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static XmppP2PClient newXmppP2PHttpClient(final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress,
        final SSLSocketFactory socketFactory,
        final ServerSocketFactory serverSocketFactory,
        final InetSocketAddress plainTextRelayAddress,
        final boolean useRelay) throws IOException {
        log.info("Creating XMPP P2P instance");
        
        // This listener listens for sockets the server side of P2P and 
        // relays their data to the local HTTP server.
        //final SessionSocketListener socketListener = 
        //    new RelayingSocketHandler(plainTextRelayAddress, null, null);
        
        return newXmppP2PHttpClient(protocol, natPmpService, 
            upnpService, serverAddress, socketFactory, serverSocketFactory, 
            plainTextRelayAddress, new SessionSocketListener() {
                
                public void onSocket(String id, Socket sock) throws IOException {
                }

                @Override
                public void reconnected() {
                    
                }
            }, useRelay);
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @param natPmpService The NAT PMP implementation.
     * @param upnpService The UPnP implementation.
     * @param socketFactory The factory for creating plain TCP sockets. This
     * could be an SSL socket factory, for example, to create SSL connections
     * to peers when connecting over TCP.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static XmppP2PClient newXmppP2PHttpClient(final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress,
        final SocketFactory socketFactory,
        final ServerSocketFactory serverSocketFactory,
        final InetSocketAddress plainTextRelayAddress,
        final SessionSocketListener callSocketListener,
        final boolean useRelay) throws IOException {
        log.info("Creating XMPP P2P instance");
        final MappedTcpAnswererServer answererServer =
            new MappedTcpAnswererServer(natPmpService, upnpService, 
                serverAddress);
        return newXmppP2PHttpClient(protocol, natPmpService, upnpService, 
            answererServer, socketFactory, serverSocketFactory, 
            plainTextRelayAddress, callSocketListener, useRelay);
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @param natPmpService The NAT PMP implementation.
     * @param upnpService The UPnP implementation.
     * @param socketFactory The factory for creating plain TCP sockets. This
     * could be an SSL socket factory, for example, to create SSL connections
     * to peers when connecting over TCP.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static XmppP2PClient newXmppP2PHttpClient(final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final MappedServerSocket mappedServer,
        final SocketFactory socketFactory,
        final ServerSocketFactory serverSocketFactory,
        final InetSocketAddress plainTextRelayAddress,
        final SessionSocketListener callSocketListener,
        final boolean useRelay) throws IOException {
        log.debug("Creating XMPP P2P instance");
        
        final OfferAnswerFactory<FiveTuple> offerAnswerFactory = 
            newIceOfferAnswerFactory(natPmpService, upnpService,
                mappedServer, socketFactory, serverSocketFactory, useRelay);

        // Now construct all the XMPP classes and link them to HTTP client.
        final XmppP2PClient client = 
            ControlXmppP2PClient.newGoogleTalkDirectClient(offerAnswerFactory,
                plainTextRelayAddress, callSocketListener, 
                DEFAULT_RELAY_WAIT_TIME, new PublicIpAddress(), 
                socketFactory);
        
        if (StringUtils.isNotBlank(protocol)) {
            final ProtocolSocketFactory sf = 
                new XmppProtocolSocketFactory(client, 
                    new DefaultXmppUriFactory());
            final Protocol sipProtocol = new Protocol(protocol, sf, 80);
            Protocol.registerProtocol(protocol, sipProtocol);
        }
        return client;
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @param natPmpService The NAT PMP implementation.
     * @param upnpService The UPnP implementation.
     * @param socketFactory The factory for creating plain TCP sockets. This
     * could be an SSL socket factory, for example, to create SSL connections
     * to peers when connecting over TCP.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static XmppP2PClient newXmppP2PHttpClient(final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress,
        final SocketFactory socketFactory,
        final ServerSocketFactory serverSocketFactory,
        final InetSocketAddress plainTextRelayAddress,
        final SessionSocketListener callSocketListener,
        final boolean useRelay, final String host, final int port, 
        final String serviceName) throws IOException {
        log.info("Creating XMPP P2P instance");
        
        final MappedTcpAnswererServer mappedServer =
            new MappedTcpAnswererServer(natPmpService, upnpService, 
                serverAddress);
        final OfferAnswerFactory<FiveTuple> offerAnswerFactory = 
            newIceOfferAnswerFactory(natPmpService, upnpService,
                mappedServer, socketFactory, serverSocketFactory, useRelay);

        // Now construct all the XMPP classes and link them to HTTP client.
        final XmppP2PClient client = 
            ControlXmppP2PClient.newClient(offerAnswerFactory,
                plainTextRelayAddress, callSocketListener, 
                DEFAULT_RELAY_WAIT_TIME, new PublicIpAddress(), 
                socketFactory, host, port, serviceName);
        
        if (StringUtils.isNotBlank(protocol)) {
            final ProtocolSocketFactory sf = 
                new XmppProtocolSocketFactory(client, 
                    new DefaultXmppUriFactory());
            final Protocol sipProtocol = new Protocol(protocol, sf, 80);
            Protocol.registerProtocol(protocol, sipProtocol);
        }
        return client;
    }

    private static OfferAnswerFactory<FiveTuple> newIceOfferAnswerFactory(
        final NatPmpService natPmpService, final UpnpService upnpService, 
        final MappedServerSocket answererServer, 
        final SocketFactory socketFactory, 
        final ServerSocketFactory serverSocketFactory,
        final boolean useRelay) {
        
        // We hard-code this instead of looking it up to avoid the DNS
        // control point.
        final CandidateProvider<InetSocketAddress> stunCandidateProvider =
            new CandidateProvider<InetSocketAddress>() {

                public Collection<InetSocketAddress> getCandidates() {
                    return StunServerRepository.getServers();
                }

                public InetSocketAddress getCandidate() {
                    return getCandidates().iterator().next();
                }
            };
        //final CandidateProvider<InetSocketAddress> stunCandidateProvider =
        //    new DnsSrvCandidateProvider("_stun._udp.littleshoot.org");
        final CandidateProvider<InetSocketAddress> turnCandidateProvider;
        if (useRelay) {
            turnCandidateProvider =
                new DnsSrvCandidateProvider("_turn._tcp.littleshoot.org");
        } else {
            turnCandidateProvider = new CandidateProvider<InetSocketAddress>() {
                @Override
                public Collection<InetSocketAddress> getCandidates() {
                    return new ArrayList<InetSocketAddress>(0);
                }
                @Override
                public InetSocketAddress getCandidate() {
                    return null;
                }
            };
        }
    
        final IceMediaStreamFactory mediaStreamFactory = 
            new IceMediaStreamFactoryImpl(stunCandidateProvider);
        //final UdpSocketFactory udpSocketFactory = new UdtSocketFactory();
        final UdpSocketFactory<FiveTuple> endpointFactory =
                new EndpointFactory();
            //new BarchartUdtSocketFactory(socketFactory);
        
        //final MappedTcpAnswererServer answererServer =
          //  new MappedTcpAnswererServer(natPmpService, upnpService, 
            //    serverAddress);
        
        final MappedTcpOffererServerPool offererServer =
            new MappedTcpOffererServerPool(natPmpService, upnpService,
                serverSocketFactory);

        final TurnClientListener clientListener =
            new ServerDataFeeder(answererServer.getHostAddress());
        
        return new IceOfferAnswerFactory<FiveTuple>(mediaStreamFactory, endpointFactory, 
            turnCandidateProvider, answererServer, clientListener, 
            stunCandidateProvider, offererServer, socketFactory);
    }
    
    private static NatPmpService emptyNatPmpService() {
        return new NatPmpService() {

            public void removeNatPmpMapping(final int mappingIndex) {
            }

            public int addNatPmpMapping(final PortMappingProtocol protocol,
                    final int localPort, final int externalPortRequested,
                    final PortMapListener portMapListener) {
                return 0;
            }

            @Override
            public void shutdown() {
                
            }
        };
    }

    private static UpnpService emptyUpnpService() {
        return new UpnpService() {

            public void removeUpnpMapping(final int mappingIndex) {
            }

            public int addUpnpMapping(final PortMappingProtocol protocol,
                    final int localPort, final int externalPortRequested,
                    final PortMapListener portMapListener) {
                return 0;
            }

            @Override
            public void shutdown() {
                // TODO Auto-generated method stub
                
            }
        };
    }
}

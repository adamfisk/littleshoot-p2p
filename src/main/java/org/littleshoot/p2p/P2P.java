package org.littleshoot.p2p;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.lang.StringUtils;
import org.lastbamboo.common.ice.IceMediaStreamDesc;
import org.lastbamboo.common.ice.IceMediaStreamFactory;
import org.lastbamboo.common.ice.IceMediaStreamFactoryImpl;
import org.lastbamboo.common.ice.IceOfferAnswerFactory;
import org.lastbamboo.common.ice.MappedTcpAnswererServer;
import org.lastbamboo.common.ice.MappedTcpOffererServerPool;
import org.lastbamboo.common.ice.UdpSocketFactory;
import org.lastbamboo.common.ice.UdtSocketFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerListenerImpl;
import org.lastbamboo.common.p2p.P2PClient;
import org.lastbamboo.common.portmapping.NatPmpService;
import org.lastbamboo.common.portmapping.PortMapListener;
import org.lastbamboo.common.portmapping.PortMappingProtocol;
import org.lastbamboo.common.portmapping.UpnpService;
import org.lastbamboo.common.sip.bootstrap.ProxyRegistrarFactory;
import org.lastbamboo.common.sip.bootstrap.ProxyRegistrarFactoryImpl;
import org.lastbamboo.common.sip.bootstrap.RobustProxyRegistrarFactory;
import org.lastbamboo.common.sip.bootstrap.RobustProxyRegistrarFactoryImpl;
import org.lastbamboo.common.sip.bootstrap.SipClientLauncher;
import org.lastbamboo.common.sip.client.SipClientTracker;
import org.lastbamboo.common.sip.client.SipClientTrackerImpl;
import org.lastbamboo.common.sip.httpclient.SipProtocolSocketFactory;
import org.lastbamboo.common.sip.stack.IdleSipSessionListener;
import org.lastbamboo.common.sip.stack.SipUriFactory;
import org.lastbamboo.common.sip.stack.SipUriFactoryImpl;
import org.lastbamboo.common.sip.stack.message.SipMessageFactory;
import org.lastbamboo.common.sip.stack.message.SipMessageFactoryImpl;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactory;
import org.lastbamboo.common.sip.stack.message.header.SipHeaderFactoryImpl;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionFactory;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionFactoryImpl;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTracker;
import org.lastbamboo.common.sip.stack.transaction.client.SipTransactionTrackerImpl;
import org.lastbamboo.common.sip.stack.transport.SipTcpTransportLayer;
import org.lastbamboo.common.sip.stack.transport.SipTcpTransportLayerImpl;
import org.lastbamboo.common.sip.stack.util.UriUtils;
import org.lastbamboo.common.sip.stack.util.UriUtilsImpl;
import org.lastbamboo.common.stun.stack.StunConstants;
import org.lastbamboo.common.turn.client.TurnClientListener;
import org.lastbamboo.common.turn.http.server.ServerDataFeeder;
import org.littleshoot.util.CandidateProvider;
import org.littleshoot.util.DnsSrvCandidateProvider;
import org.littleshoot.util.RelayingSocketHandler;
import org.littleshoot.util.SocketListener;
import org.littleshoot.commom.xmpp.DefaultXmppP2PClient;
import org.littleshoot.commom.xmpp.DefaultXmppUriFactory;
import org.littleshoot.commom.xmpp.XmppP2PClient;
import org.littleshoot.commom.xmpp.XmppProtocolSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that builds all the elements of the LittleShoot P2P platform.
 */
public class P2P {
    
    /**
     * The logger for this class.
     */
    private final static Logger log = LoggerFactory.getLogger(P2P.class);
    
    static {
        // Use Google Public DNS.
        System.setProperty("sun.net.spi.nameservice.nameservers", 
            "8.8.8.8,8.8.4.4");
    }
    
    /**
     * Creates a new LittleShoot P2P instance with all the default settings,
     * with TCP, UDP, and TURN relay transports all turned on.
     * 
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static P2PClient newSipP2PClient(
        final InetSocketAddress serverAddress) throws IOException {
        return newSipP2PClient(new IceMediaStreamDesc(true, true, "message", 
            "http", 1, true), serverAddress);
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file.
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static P2PClient newSipP2PClient(
        final IceMediaStreamDesc streamDesc, 
        final InetSocketAddress serverAddress) throws IOException {
        return newSipP2PClient(streamDesc, "", emptyNatPmpService(), 
            emptyUpnpService(), serverAddress);
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file.
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @param protocol The name of the protocol that should trigger P2P 
     * connections.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static P2PClient newSipP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol,
        final InetSocketAddress serverAddress) throws IOException {
        return newSipP2PClient(streamDesc, protocol, emptyNatPmpService(), 
            emptyUpnpService(), serverAddress);
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @param natPmpService The NAT PMP implementation.
     * @param upnpService The UPnP implementation.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static P2PClient newSipP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress) throws IOException {
        return newSipP2PClient(streamDesc, protocol, natPmpService, 
            upnpService, serverAddress, SocketFactory.getDefault(),
            ServerSocketFactory.getDefault());
    }

    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file
     * and allowing custom classes for NAT PMP and UPnP mappings. 
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @param natPmpService The NAT PMP implementation.
     * @param upnpService The UPnP implementation.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static P2PClient newSipP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress,
        final SocketFactory socketFactory,
        final ServerSocketFactory serverSocketFactory) throws IOException {
        log.info("Creating P2P instance");
        
        // This listener listens for sockets the server side of P2P and 
        // relays their data to the local HTTP server.
        final SocketListener socketListener = 
            new RelayingSocketHandler(serverAddress);
        
        final OfferAnswerFactory offerAnswerFactory = 
            newIceOfferAnswerFactory(streamDesc, natPmpService, upnpService,
                socketListener, serverAddress, socketFactory,
                serverSocketFactory);

        // Now construct all the SIP classes and link them to HTTP client.
        final SipClientTracker sipClientTracker = new SipClientTrackerImpl();
        final SipUriFactory sipUriFactory = new SipUriFactoryImpl();
        
        // Note the last argument for how long to wait before using a relay
        // is in seconds!!
        final P2PClient client = newSipClientLauncher(sipClientTracker,
            offerAnswerFactory, socketListener, sipUriFactory, 20);
        
        if (StringUtils.isNotBlank(protocol)) {
            final ProtocolSocketFactory sf = 
                new SipProtocolSocketFactory(client, sipUriFactory);
            final Protocol sipProtocol = 
                new Protocol(protocol, sf, 80);
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
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static XmppP2PClient newXmppP2PClient(
        final IceMediaStreamDesc streamDesc, 
        final InetSocketAddress serverAddress) throws IOException {
        return newXmppP2PClient(streamDesc, "shoot", emptyNatPmpService(), 
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
    public static P2PClient newXmppP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol,
        final InetSocketAddress serverAddress) throws IOException {
        return newXmppP2PClient(streamDesc, protocol, emptyNatPmpService(), 
            emptyUpnpService(), serverAddress);
    }
    
    public static XmppP2PClient newXmppP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress) 
        throws IOException {
        return newXmppP2PClient(streamDesc, protocol, natPmpService,
            upnpService, serverAddress, SocketFactory.getDefault(),
            ServerSocketFactory.getDefault(), serverAddress);
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
    public static XmppP2PClient newXmppP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService,
        final InetSocketAddress serverAddress,
        final SocketFactory socketFactory,
        final ServerSocketFactory serverSocketFactory,
        final InetSocketAddress plainTextRelayAddress) throws IOException {
        log.info("Creating XMPP P2P instance");
        
        // This listener listens for sockets the server side of P2P and 
        // relays their data to the local HTTP server.
        final SocketListener socketListener = 
            new RelayingSocketHandler(plainTextRelayAddress);
        
        final OfferAnswerFactory offerAnswerFactory = 
            newIceOfferAnswerFactory(streamDesc, natPmpService, upnpService,
                socketListener, serverAddress, socketFactory, 
                serverSocketFactory);

        // Now construct all the XMPP classes and link them to HTTP client.
        final XmppP2PClient client = newXmppSignalingCLient(
            offerAnswerFactory, socketListener, 20);
        
        if (StringUtils.isNotBlank(protocol)) {
            final ProtocolSocketFactory sf = 
                new XmppProtocolSocketFactory(client, 
                    new DefaultXmppUriFactory());
            final Protocol sipProtocol = new Protocol(protocol, sf, 80);
            Protocol.registerProtocol(protocol, sipProtocol);
        }
        return client;
    }
    
    private static XmppP2PClient newXmppSignalingCLient(
        final OfferAnswerFactory offerAnswerFactory, 
        final SocketListener socketListener, final int relayWaitTime) {
        // So there are really two classes that send relay ICE-negotiated 
        // sockets to the HTTP server. The first is the "mapped" server above
        // that accepts *incoming* sockets from the controlling offerer and
        // forwards them. The second is created here that handles outgoing
        // sockets created from the answerer to the offerer. They both do more
        // or less the same thing, but one works for incoming, the other for
        // outgoing.
        final OfferAnswerListener offerAnswerListener = 
            new OfferAnswerListenerImpl(socketListener);
        
        return new DefaultXmppP2PClient(offerAnswerFactory,
            offerAnswerListener, relayWaitTime);
    }
    
    private static OfferAnswerFactory newIceOfferAnswerFactory(
        final IceMediaStreamDesc streamDesc, 
        final NatPmpService natPmpService, final UpnpService upnpService, 
        final SocketListener socketListener, 
        final InetSocketAddress serverAddress, 
        final SocketFactory socketFactory, 
        final ServerSocketFactory serverSocketFactory) throws IOException {
        
        // We hard-code this instead of looking it up to avoid the DNS
        // control point.
        final CandidateProvider<InetSocketAddress> stunCandidateProvider =
            new CandidateProvider<InetSocketAddress>() {

                public Collection<InetSocketAddress> getCandidates() {
                    return Arrays.asList(StunConstants.SERVERS);
                }

                public InetSocketAddress getCandidate() {
                    return getCandidates().iterator().next();
                }
            };
        //final CandidateProvider<InetSocketAddress> stunCandidateProvider =
        //    new DnsSrvCandidateProvider("_stun._udp.littleshoot.org");
        
        final CandidateProvider<InetSocketAddress> turnCandidateProvider =
            new DnsSrvCandidateProvider("_turn._tcp.littleshoot.org");
    
        final IceMediaStreamFactory mediaStreamFactory = 
            new IceMediaStreamFactoryImpl(streamDesc, stunCandidateProvider);
        final UdpSocketFactory udpSocketFactory = new UdtSocketFactory();
        
        final MappedTcpAnswererServer answererServer =
            new MappedTcpAnswererServer(natPmpService, upnpService, 
                serverAddress);
        
        final MappedTcpOffererServerPool offererServer =
            new MappedTcpOffererServerPool(natPmpService, upnpService,
                serverSocketFactory);

        final TurnClientListener clientListener =
            new ServerDataFeeder(serverAddress);
        
        return new IceOfferAnswerFactory(mediaStreamFactory, udpSocketFactory, 
            streamDesc, turnCandidateProvider, natPmpService, upnpService,
            answererServer, clientListener, stunCandidateProvider,
            offererServer, socketFactory);
    }

    private static SipClientLauncher newSipClientLauncher(
        final SipClientTracker sipClientTracker, 
        final OfferAnswerFactory offerAnswerFactory, 
        final SocketListener socketListener, final SipUriFactory sipUriFactory, 
        final int relayWaitTime) {
        final UriUtils uriUtils = new UriUtilsImpl();
        final CandidateProvider<InetSocketAddress> sipCandidateProvider =
            new DnsSrvCandidateProvider("_sip._tcp2.littleshoot.org");
        
        final SipHeaderFactory headerFactory = new SipHeaderFactoryImpl();
        final SipMessageFactory messageFactory = new SipMessageFactoryImpl();
        final SipTransactionTracker transactionTracker = 
            new SipTransactionTrackerImpl();
        final SipTransactionFactory transactionFactory = 
            new SipTransactionFactoryImpl(transactionTracker, messageFactory, 
                500);
        final SipTcpTransportLayer transportLayer = 
            new SipTcpTransportLayerImpl(transactionFactory, headerFactory, 
                messageFactory);
        
        // So there are really two classes that send relay ICE-negotiated 
        // sockets to the HTTP server. The first is the "mapped" server above
        // that accepts *incoming* sockets from the controlling offerer and
        // forwards them. The second is created here that handles outgoing
        // sockets created from the answerer to the offerer. They both do more
        // or less the same thing, but one works for incoming, the other for
        // outgoing.
        final OfferAnswerListener offerAnswerListener = 
            new OfferAnswerListenerImpl(socketListener);
        final IdleSipSessionListener idleSipSessionListener = 
            new IdleSipSessionListener() {
                public void onIdleSession() {
                }
            };
            
        final ProxyRegistrarFactory registrarFactory =
            new ProxyRegistrarFactoryImpl(messageFactory, transportLayer, 
                transactionTracker, sipClientTracker, uriUtils, 
                offerAnswerFactory,offerAnswerListener, idleSipSessionListener);
        
        final RobustProxyRegistrarFactory robustRegistrarFactory = 
            new RobustProxyRegistrarFactoryImpl(uriUtils, sipCandidateProvider, 
                registrarFactory);
        
        return new SipClientLauncher(sipClientTracker, robustRegistrarFactory, 
            sipUriFactory, offerAnswerFactory, relayWaitTime);
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
        };
    }
}

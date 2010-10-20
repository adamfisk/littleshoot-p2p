package org.littleshoot.p2p;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.lang.StringUtils;
import org.lastbamboo.common.ice.IceMediaStreamDesc;
import org.lastbamboo.common.ice.IceMediaStreamFactory;
import org.lastbamboo.common.ice.IceOfferAnswerFactory;
import org.lastbamboo.common.ice.MappedTcpAnswererServer;
import org.lastbamboo.common.ice.UdpSocketFactory;
import org.lastbamboo.common.ice.UdtSocketFactory;
import org.lastbamboo.common.ice.rudp.IceMediaStreamFactoryImpl;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerListenerImpl;
import org.lastbamboo.common.p2p.P2PSignalingClient;
import org.lastbamboo.common.portmapping.NatPmpService;
import org.lastbamboo.common.portmapping.PortMapListener;
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
import org.lastbamboo.common.turn.client.TurnClientListener;
import org.lastbamboo.common.turn.http.server.ServerDataFeeder;
import org.lastbamboo.common.util.CandidateProvider;
import org.lastbamboo.common.util.DnsSrvCandidateProvider;
import org.lastbamboo.common.util.NetworkUtils;
import org.lastbamboo.common.util.RelayingSocketHandler;
import org.lastbamboo.common.util.ShootConstants;
import org.lastbamboo.common.util.SocketListener;
import org.littleshoot.commom.xmpp.DefaultXmppUriFactory;
import org.littleshoot.commom.xmpp.XmppP2PSignalingClient;
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
    public static P2PSignalingClient newSipP2PClient() throws IOException {
        return newSipP2PClient(new IceMediaStreamDesc(true, true, "message", 
            "http", 1, true));
    }
    
    /**
     * Creates a new LittleShoot P2P instance with a custom configuration file.
     * 
     * @param streamDesc A configuration class allowing the caller to specify
     * things like whether or not the use TCP, UDP, and TURN relay connections.
     * @throws IOException If any of the necessary network configurations 
     * cannot be established.
     */
    public static P2PSignalingClient newSipP2PClient(
        final IceMediaStreamDesc streamDesc) throws IOException {
        return newSipP2PClient(streamDesc, "", emptyNatPmpService(), 
            emptyUpnpService());
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
    public static P2PSignalingClient newSipP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol) 
        throws IOException {
        return newSipP2PClient(streamDesc, protocol, emptyNatPmpService(), 
            emptyUpnpService());
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
    public static P2PSignalingClient newSipP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService) 
        throws IOException {
        log.info("Creating P2P instance");
        final InetAddress localServerAddress = NetworkUtils.getLocalHost();
        
        // This listener listens for sockets the server side of P2P and 
        // relays their data to the local HTTP server.
        final SocketListener socketListener = 
            new RelayingSocketHandler(localServerAddress, 
                ShootConstants.HTTP_PORT);
        
        final OfferAnswerFactory offerAnswerFactory = 
            newIceOfferAnswerFactory(streamDesc, natPmpService, upnpService,
                socketListener, localServerAddress);

        // Now construct all the SIP classes and link them to HTTP client.
        final SipClientTracker sipClientTracker = new SipClientTrackerImpl();
        final SipUriFactory sipUriFactory = new SipUriFactoryImpl();
        
        // Note the last argument for how long to wait before using a relay
        // is in seconds!!
        final P2PSignalingClient client = newSipClientLauncher(sipClientTracker,
            offerAnswerFactory, socketListener, sipUriFactory, 20);
        
        if (StringUtils.isNotBlank(protocol)) {
            final ProtocolSocketFactory socketFactory = 
                new SipProtocolSocketFactory(client, sipUriFactory);
            final Protocol sipProtocol = 
                new Protocol(protocol, socketFactory, 80);
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
    public static P2PSignalingClient newXmppP2PClient(
        final IceMediaStreamDesc streamDesc) throws IOException {
        return newXmppP2PClient(streamDesc, "shoot", emptyNatPmpService(), 
            emptyUpnpService());
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
    public static P2PSignalingClient newXmppP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol) 
        throws IOException {
        return newXmppP2PClient(streamDesc, protocol, emptyNatPmpService(), 
            emptyUpnpService());
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
    public static P2PSignalingClient newXmppP2PClient(
        final IceMediaStreamDesc streamDesc, final String protocol, 
        final NatPmpService natPmpService, final UpnpService upnpService) 
        throws IOException {
        log.info("Creating XMPP P2P instance");
        final InetAddress localServerAddress = NetworkUtils.getLocalHost();
        
        // This listener listens for sockets the server side of P2P and 
        // relays their data to the local HTTP server.
        final SocketListener socketListener = 
            new RelayingSocketHandler(localServerAddress, 
                ShootConstants.HTTP_PORT);
        
        final OfferAnswerFactory offerAnswerFactory = 
            newIceOfferAnswerFactory(streamDesc, natPmpService, upnpService,
                socketListener, localServerAddress);

        // Now construct all the XMPP classes and link them to HTTP client.
        final P2PSignalingClient client = newXmppSignalingCLient(
            offerAnswerFactory, socketListener, 20);
        
        if (StringUtils.isNotBlank(protocol)) {
            final ProtocolSocketFactory socketFactory = 
                new XmppProtocolSocketFactory(client, 
                    new DefaultXmppUriFactory());
            final Protocol sipProtocol = 
                new Protocol(protocol, socketFactory, 80);
            Protocol.registerProtocol(protocol, sipProtocol);
        }
        return client;
    }
    
    private static P2PSignalingClient newXmppSignalingCLient(
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
        
        return new XmppP2PSignalingClient(offerAnswerFactory,
            offerAnswerListener, relayWaitTime);
    }
    
    private static OfferAnswerFactory newIceOfferAnswerFactory(
        final IceMediaStreamDesc streamDesc, 
        final NatPmpService natPmpService, final UpnpService upnpService, 
        final SocketListener socketListener, 
        final InetAddress localServerAddress) throws IOException {
        final CandidateProvider<InetSocketAddress> stunCandidateProvider =
            new DnsSrvCandidateProvider("_stun._udp.littleshoot.org");
        
        final CandidateProvider<InetSocketAddress> turnCandidateProvider =
            new DnsSrvCandidateProvider("_turn._tcp.littleshoot.org");
    
        final IceMediaStreamFactory mediaStreamFactory = 
            new IceMediaStreamFactoryImpl(streamDesc, stunCandidateProvider);
        final UdpSocketFactory udpSocketFactory = new UdtSocketFactory();
        
        final MappedTcpAnswererServer answererServer =
            new MappedTcpAnswererServer(natPmpService, upnpService, 
                socketListener);

        final InetSocketAddress httpServerAddress =
            new InetSocketAddress(localServerAddress, ShootConstants.HTTP_PORT);
        
        final TurnClientListener clientListener =
            new ServerDataFeeder(httpServerAddress);
        
        return new IceOfferAnswerFactory(mediaStreamFactory, udpSocketFactory, 
            streamDesc, turnCandidateProvider, natPmpService, upnpService,
            answererServer, clientListener);
    }

    private static SipClientLauncher newSipClientLauncher(
        final SipClientTracker sipClientTracker, 
        final OfferAnswerFactory offerAnswerFactory, 
        final SocketListener socketListener, 
        final SipUriFactory sipUriFactory, final int relayWaitTime) {
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
            
            public void addPortMapListener(
                final PortMapListener portMapListener) {
            }
            
            public int addNatPmpMapping(final int protocolType, 
                final  int localPort, final int externalPortRequested) {
                return 0;
            }
        };
    }

    private static UpnpService emptyUpnpService() {
        return new UpnpService() {
            
            public void removeUpnpMapping(final int mappingIndex) {
            }
            
            public int addUpnpMapping(final int protocolType, 
                final int localPort, final int externalPortRequested) {
                return 0;
            }
            
            public void addPortMapListener(
                final PortMapListener portMapListener) {
            }
        };
    }
}

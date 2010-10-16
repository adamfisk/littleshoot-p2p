package org.littleshoot.p2p;

import java.net.URI;

import org.junit.Test;
import org.lastbamboo.common.ice.IceMediaStreamDesc;
import org.lastbamboo.common.offer.answer.OfferAnswerMessage;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.p2p.P2PSignalingClient;
import org.littleshoot.p2p.P2P;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppTest {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Test public void testXmppRelaying() throws Exception {
        final IceMediaStreamDesc streamDesc = 
            new IceMediaStreamDesc(false, true, "message", "http", 1, 
                false);
        final P2PSignalingClient client1 = P2P.newXmppP2PClient(streamDesc);
        final String jid1 = client1.login("adamfisk@gmail.com", "1745t77q");
        
        final P2PSignalingClient client2 = P2P.newXmppP2PClient(streamDesc);
        final String jid2 = 
            client2.login("mglittleshoot@gmail.com", "#@$/mg77q");
        
        final URI uri = new URI(jid2);
        final byte[] offer = "Hey there".getBytes();
        final OfferAnswerTransactionListener transactionListener =
            new OfferAnswerTransactionListener() {
                
                public void onTransactionSucceeded(OfferAnswerMessage message) {
                    System.out.println("SUCCESS!!");
                }
                
                public void onTransactionFailed(OfferAnswerMessage message) {
                    System.out.println("FAILURE!!");
                }
            };
        System.out.println("offer");
        log.info("Sending offer to "+uri);
        client1.offer(uri, offer, transactionListener);
    }

}

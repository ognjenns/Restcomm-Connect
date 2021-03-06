/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */

package org.restcomm.connect.testsuite.telephony;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sip.address.SipURI;
import javax.sip.header.Header;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.gson.JsonObject;

/**
 * Test for Dial Action attribute. Reference: https://www.twilio.com/docs/api/twiml/dial#attributes-action The 'action'
 * attribute takes a URL as an argument. When the dialed call ends, Restcomm will make a GET or POST request to this URL
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
@Category(value={FeatureAltTests.class, ParallelClassTests.class})
public class DialActionAnswerDelayTest {

    private final static Logger logger = Logger.getLogger(DialActionAnswerDelayTest.class.getName());

    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
    private static final String body = new String(bytes);

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static int mockPort = NetworkPortAssigner.retrieveNextPortByFile();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;
    private static SipStackTool tool4;

    // Bob is a simple SIP Client. Will not register with Restcomm
    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:bob@127.0.0.1:" + bobPort;

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort;

    // Henrique is a simple SIP Client. Will not register with Restcomm
    private SipStack henriqueSipStack;
    private SipPhone henriquePhone;
    private static String henriquePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String henriqueContact = "sip:henrique@127.0.0.1:" + henriquePort;

    // George is a simple SIP Client. Will not register with Restcomm
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private static String georgePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String georgeContact = "sip:+131313@127.0.0.1:" + georgePort;


    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;
    private static String dialClientWithActionUrl = "sip:+12223334455@" + restcommContact; // Application: dial-client-entry_wActionUrl.xml

    private String dialActionRcml = "<Response><Dial><Number>+131313</Number></Dial></Response>";
    private String playPlusDialActionRcml;
    private String dialActionRcmlPlay;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialActionAnswerDelayTest1");
        tool2 = new SipStackTool("DialActionAnswerDelayTest2");
        tool3 = new SipStackTool("DialActionAnswerDelayTest3");
        tool4 = new SipStackTool("DialActionAnswerDelayTest4");
    }

    public static void reconfigurePorts() {
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort;
            dialClientWithActionUrl = "sip:+12223334455@" + restcommContact;
        }
        if (System.getProperty("arquillian_http_port") != null) {
            restcommHTTPPort = Integer.valueOf(System.getProperty("arquillian_http_port"));
        }
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort, restcommContact);
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort, restcommContact);
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact);

        henriqueSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", henriquePort, restcommContact);
        henriquePhone = henriqueSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, henriqueContact);

        georgeSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", georgePort, restcommContact);
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, georgeContact);
        dialActionRcmlPlay = "<Response><Play>"+deploymentUrl.toString()+"audio/demo-prompt.wav</Play></Response>";
        playPlusDialActionRcml = "<Response><Play>"+deploymentUrl.toString()+"audio/demo-prompt.wav</Play><Dial><Number>+131313</Number></Dial></Response>";

    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
        }

        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }
        if (alicePhone != null) {
            alicePhone.dispose();
        }

        if (henriqueSipStack != null) {
            henriqueSipStack.dispose();
        }
        if (henriquePhone != null) {
            henriquePhone.dispose();
        }

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    @Test
    @Category(UnstableTests.class)
    public void testDialActionInvalidCall() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.SERVER_INTERNAL_ERROR, bobCall.getLastReceivedResponse().getStatusCode());

        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        assertTrue(requestBody.contains("DialCallStatus=failed"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertTrue(dialCallSid.equals("null"));
        //Since ALICE is not registered, CallManager will ask to hangup the call, thus we never have outbound call
//        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
//        assertNotNull(cdr);
    }

    @Test //No regression test for https://github.com/Mobicents/RestComm/issues/505
    @Category(UnstableTests.class)
    public void testDialActionInvalidCallCheckCallStatusCompleted() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.SERVER_INTERNAL_ERROR, bobCall.getLastReceivedResponse().getStatusCode());

        Thread.sleep(10 * 1000);

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        //DialCallStatus should be null since there was no call made - since Alice is not registered
        assertTrue(requestBody.contains("DialCallStatus=failed"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        assertTrue(requestBody.contains("CallStatus=wait-for-answer"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertTrue(dialCallSid.equals("null"));
        //Since ALICE is not registered, CallManager will ask to hangup the call, thus we never have outbound call
//        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
//        assertNotNull(cdr);
    }

    @Test
    @Category(WithInMinsTests.class)
    public void testDialActionAliceAnswers() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        aliceCall.disconnect();

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        assertTrue(bobCall.respondToDisconnect());
        try {
            Thread.sleep(50 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        Thread.sleep(3000);

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        assertTrue(requestBody.contains("DialCallStatus=completed"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialCallDuration=3"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test
    public void testDialActionAliceAnswersAliceHangup() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        aliceCall.disconnect();

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        assertTrue(bobCall.respondToDisconnect());
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        assertTrue(requestBody.contains("DialCallStatus=completed"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialCallDuration=3"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test
    public void testDialActionAliceAnswersBobDisconnects() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        bobCall.disconnect();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        assertTrue(aliceCall.respondToDisconnect());
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        assertTrue(requestBody.contains("DialCallStatus=completed"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialCallDuration=3"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test
    public void testDialActionAliceNOAnswer() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.listenForCancel());
        SipTransaction cancelTransaction = aliceCall.waitForCancel(30 * 1000);
        assertNotNull(cancelTransaction);
        assertTrue(aliceCall.respondToCancel(cancelTransaction, Response.OK, "Alice-OK-2-Cancel", 3600));

        assertTrue(bobCall.waitOutgoingCallResponse(120 * 1000));
        assertEquals(Response.REQUEST_TIMEOUT, bobCall.getLastReceivedResponse().getStatusCode());

        Thread.sleep(3700);

        Thread.sleep(10000);

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        if (!requestBody.contains("DialCallStatus=no-answer")) {
            String msgToPrint = requestBody.replaceAll("&", "\n");
            logger.info("requestBody: \n"+"\n ---------------------- \n"+msgToPrint+"\n---------------------- ");
        }
        assertTrue(requestBody.contains("DialCallStatus=canceled"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialRingDuration=3"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test
    public void testDialActionAliceBusy() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.BUSY_HERE, "Busy-Alice", 3600));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.BUSY_HERE, bobCall.getLastReceivedResponse().getStatusCode());

        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        if (!requestBody.contains("DialCallStatus=busy")) {
            logger.info("requestBody: \n"+requestBody);
        }
        assertTrue(requestBody.contains("DialCallStatus=busy"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test
    @Category(WithInMinsTests.class)
    public void testSipInviteCustomHeaders() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        ArrayList<String> additionalHeaders = new ArrayList<String>();
        Header customHeader = aliceSipStack.getHeaderFactory().createHeader("X-My-Custom-Header", "My Custom Value");
        Header otherHeader = aliceSipStack.getHeaderFactory().createHeader("X-OtherHeader", "Other Value");
        Header anotherHeader = aliceSipStack.getHeaderFactory().createHeader("X-another-header", "another value");
        additionalHeaders.add(customHeader.toString());
        additionalHeaders.add(otherHeader.toString());
        additionalHeaders.add(anotherHeader.toString());
//        bobCall.initiateOutgoingCall(fromUri, toUri, viaNonProxyRoute, body, contentType, contentSubType, additionalHeaders, replaceHeaders)
//        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, additionalHeaders, null, body);
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", additionalHeaders, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));

        // Add custom headers to the SIP INVITE
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
//        public boolean sendIncomingCallResponse(int statusCode,
//                String reasonPhrase, int expires, String body, String contentType,
//                String contentSubType, ArrayList<String> additionalHeaders,
//                ArrayList<String> replaceHeaders)
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null, null));
//        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, null, null, receivedBody));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        aliceCall.disconnect();

        bobCall.listenForDisconnect();
            assertTrue(bobCall.waitForDisconnect(30 * 1000));
        assertTrue(bobCall.respondToDisconnect());
        try {
            Thread.sleep(50 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        assertTrue(requestBody.contains("SipHeader_X-My-Custom-Header=My+Custom+Value"));
        assertTrue(requestBody.contains("SipHeader_X-OtherHeader=Other+Value"));
        assertTrue(requestBody.contains("SipHeader_X-another-header=another+value"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test //TODO: PASSES when run individually. to check
    @Category(WithInMinsTests.class)
    public void testDialCallDurationAliceAnswers() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        Thread.sleep(2000); //Ringing time
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());

        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000); //Talk time

        // hangup.
        aliceCall.disconnect();

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        assertTrue(bobCall.respondToDisconnect());
        try {
            Thread.sleep(50 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }


        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        assertTrue(requestBody.contains("DialCallStatus=completed"));
        assertTrue(requestBody.contains("DialCallDuration=3"));
        assertTrue(requestBody.contains("DialRingDuration=2"));

        Iterator iter = Arrays.asList(params).iterator();
        String callSid = null;
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("CallSid")) {
                callSid = param.split("=")[1];
            } else if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
            }

        }
        assertNotNull(callSid);
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject dialCdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
        assertNotNull(dialCdr);

        //INBOUND call has no ring_duration since Restcomm will answer imediatelly an incoming call
        assertTrue(cdr.get("duration").getAsString().equalsIgnoreCase("5")); //Only talk time
        assertTrue(cdr.get("direction").getAsString().equalsIgnoreCase("inbound"));

        assertTrue(dialCdr.get("duration").getAsString().equalsIgnoreCase("3")); //Only talk time
        assertTrue(dialCdr.get("ring_duration").getAsString().equalsIgnoreCase("2")); //Only Ringing time
        assertTrue(dialCdr.get("direction").getAsString().equalsIgnoreCase("outbound-api"));
    }


    @Test //TODO: PASSES when run individually. to check
    public void testDialCallDurationAliceBusy() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);

        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        Thread.sleep(2000); //Ringing Time
        assertTrue(aliceCall.sendIncomingCallResponse(Response.BUSY_HERE, "Busy-Alice", 3600));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.BUSY_HERE, bobCall.getLastReceivedResponse().getStatusCode());

        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }



        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        assertTrue(requestBody.contains("DialCallStatus=busy"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        assertTrue(requestBody.contains("DialRingDuration=2"));

        Iterator iter = Arrays.asList(params).iterator();
        String callSid = null;
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("CallSid")) {
                callSid = param.split("=")[1];
            } else if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
            }
        }
        assertNotNull(callSid);
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject dialCdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
        assertNotNull(dialCdr);

        //Since the outbound call to Alice never got established the duration of the inbound call is not defined
        //assertTrue(cdr.get("duration").getAsString().equalsIgnoreCase("0")); //Only talk time
        //assertTrue(cdr.get("ring_duration").getAsString().equalsIgnoreCase("0"));
        assertTrue(cdr.get("direction").getAsString().equalsIgnoreCase("inbound"));

        assertTrue(dialCdr.get("duration").getAsString().equalsIgnoreCase("0")); //Only talk time
        assertTrue(dialCdr.get("ring_duration").getAsString().equalsIgnoreCase("2")); //Only Ringing time
        assertTrue(dialCdr.get("direction").getAsString().equalsIgnoreCase("outbound-api"));
    }

    @Test
    public void testDialActionAliceNOAnswerRcmlOnDialAction() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialActionRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.listenForCancel());
        SipTransaction cancelTransaction = aliceCall.waitForCancel(30 * 1000);
        assertNotNull(cancelTransaction);
        assertTrue(aliceCall.respondToCancel(cancelTransaction, Response.OK, "Alice-OK-2-Cancel", 3600));

        assertTrue(georgeCall.waitForIncomingCall(5000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        georgeCall.disconnect();

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(5000);

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        if (!requestBody.contains("DialCallStatus=no-answer")) {
            String msgToPrint = requestBody.replaceAll("&", "\n");
            logger.info("requestBody: \n"+"\n ---------------------- \n"+msgToPrint+"\n---------------------- ");
        }
        assertTrue(requestBody.contains("DialCallStatus=canceled"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialRingDuration=3"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test
    public void testDialActionAliceNOAnswerRcmlPlayOnDialAction() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialActionRcmlPlay)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.listenForCancel());
        SipTransaction cancelTransaction = aliceCall.waitForCancel(30 * 1000);
        assertNotNull(cancelTransaction);
        assertTrue(aliceCall.respondToCancel(cancelTransaction, Response.OK, "Alice-OK-2-Cancel", 3600));

        assertTrue(bobCall.waitOutgoingCallResponse(50 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        bobCall.listenForDisconnect();
        logger.info("bob listenForDisconnect");
        assertTrue(bobCall.waitForDisconnect(120 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(5000);

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        if (!requestBody.contains("DialCallStatus=no-answer")) {
            String msgToPrint = requestBody.replaceAll("&", "\n");
            logger.info("requestBody: \n"+"\n ---------------------- \n"+msgToPrint+"\n---------------------- ");
        }
        assertTrue(requestBody.contains("DialCallStatus=canceled"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialRingDuration=3"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }

    @Test
    public void testDialActionAliceNOAnswerPlayPlusDialRcmlOnDialAction() throws ParseException, InterruptedException {

        stubFor(post(urlPathMatching("/DialAction.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(playPlusDialActionRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialClientWithActionUrl, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.listenForCancel());
        SipTransaction cancelTransaction = aliceCall.waitForCancel(30 * 1000);
        assertNotNull(cancelTransaction);
        assertTrue(aliceCall.respondToCancel(cancelTransaction, Response.OK, "Alice-OK-2-Cancel", 3600));

        Thread.sleep(6000);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        logger.info("George is going to waitForIncomingCall");
        assertTrue(georgeCall.waitForIncomingCall(12000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(georgeCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        georgeCall.disconnect();

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(5000);

        logger.info("About to check the DialAction Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/DialAction.*")));
        assertTrue(requests.size() == 1);
        String requestBody = requests.get(0).getBodyAsString();
        String[] params = requestBody.split("&");
        if (!requestBody.contains("DialCallStatus=no-answer")) {
            String msgToPrint = requestBody.replaceAll("&", "\n");
            logger.info("requestBody: \n"+"\n ---------------------- \n"+msgToPrint+"\n---------------------- ");
        }
        assertTrue(requestBody.contains("DialCallStatus=canceled"));
        assertTrue(requestBody.contains("To=%2B12223334455"));
        assertTrue(requestBody.contains("From=bob"));
        assertTrue(requestBody.contains("DialRingDuration=3"));
        assertTrue(requestBody.contains("DialCallDuration=0"));
        Iterator iter = Arrays.asList(params).iterator();
        String dialCallSid = null;
        while (iter.hasNext()) {
            String param = (String) iter.next();
            if (param.startsWith("DialCallSid")) {
                dialCallSid = param.split("=")[1];
                break;
            }
        }
        assertNotNull(dialCallSid);
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, dialCallSid);
        assertNotNull(cdr);
    }


    @Deployment(name = "DialActionAnswerDelay", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String,String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("8090", String.valueOf(mockPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5070", String.valueOf(georgePort));
        replacements.put("5090", String.valueOf(bobPort));
        replacements.put("5091", String.valueOf(alicePort));
        replacements.put("5092", String.valueOf(henriquePort));
        List<String> resources = new ArrayList(Arrays.asList("dial-client-entry_wActionUrl.xml"));
        return WebArchiveUtil.createWebArchiveNoGw("restcomm-delay.xml",
                "restcomm.script_dialActionTest",resources, replacements);
    }

}
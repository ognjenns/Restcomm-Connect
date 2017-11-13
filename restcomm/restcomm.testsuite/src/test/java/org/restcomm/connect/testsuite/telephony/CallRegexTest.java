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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import javax.sip.DialogState;
import javax.sip.address.SipURI;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipStack;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.archive.ShrinkWrapMaven;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;

/**
 * Test for Regex for IncomingPhoneNumbers
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
public class CallRegexTest {

    private final static Logger logger = Logger.getLogger(CallRegexTest.class.getName());

    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[]{118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10};
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
    private static SipStackTool tool5;

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

    // subaccountclient is a simple SIP Client. Will register with Restcomm
    private SipStack subAccountClientSipStack;
    private SipPhone subAccountClientPhone;
    private static String subAccountPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());     
    private String subAccountClientContact = "sip:subaccountclient@127.0.0.1:" + subAccountPort;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String subAccountSid = "ACae6e420f425248d6a26948c17a9e2acg";
    private String subAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    
    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;        
    private static String restcommContact = "127.0.0.1:" + restcommPort;      

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialActionTest1");
        tool2 = new SipStackTool("DialActionTest2");
        tool3 = new SipStackTool("DialActionTest3");
        tool4 = new SipStackTool("DialActionTest4");
        tool5 = new SipStackTool("DialActionTest5");
    }
    
    
    public static void reconfigurePorts() {     
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort; 
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

        subAccountClientSipStack = tool5.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", subAccountPort, restcommContact);
        subAccountClientPhone = subAccountClientSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, subAccountClientContact);
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

        if (subAccountClientPhone != null) {
            subAccountClientPhone.dispose();
        }
        if (subAccountClientSipStack != null) {
            subAccountClientSipStack.dispose();
        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    @AfterClass
    public static void afterClass() {
        System.gc();
        System.out.println("System.gc() run");
    }

    private String dialAliceRcml = "<Response><Dial><Client>alice</Client></Dial></Response>";

    @Test
    public void testDialClientAlice7777() throws ParseException, InterruptedException, MalformedURLException {
        //matches regex expression "7777|8888"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:7777@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

        @Test
    public void testDialClientAlice8888() throws ParseException, InterruptedException, MalformedURLException {
        //matches regex expression "7777|8888"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:8888@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

    @Test
    public void testDialClientAlice7711133() throws ParseException, InterruptedException, MalformedURLException {
        //matches regex expression "^*77...33#$"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:*7711133#@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

@Test
    public void testDialClientAlice7722233() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "^*77...33#$"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:*7722233#@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

@Test
    public void testDialClientAlice12233() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "^[12]2233#$"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:12233#@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

@Test
    public void testDialClientAlice22233() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "^[12]2233#$"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:22233#@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

@Test
    public void testDialClientAlice222_888_999_500() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "^\\*222\\*...\\*...\\*500#$"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:*222*888*999*500#@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }


@Test
    public void testDialClientAlice222_333_444_500() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "^\\*222\\*...\\*...\\*500#$"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:*222*333*444*500#@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }


@Test
    public void testDialClientAlice42342() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "[45]234[23]"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:42342@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

@Test
    public void testDialClientAlice52343() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "[45]234[23]"
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:52343@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

@Test
    public void testDialClientAlice999111() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "999..."
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:999111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

@Test
    public void testDialClientAlice999222() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "999..."
        stubFor(get(urlPathEqualTo("/regex"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:999222@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regex")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

    @Test
    public void testDialClientAliceNoRegex() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "999..."
        stubFor(get(urlPathEqualTo("/1313"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:1313@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1313")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

    @Test
    public void testDialClientAliceStar() throws ParseException, InterruptedException, MalformedURLException {
//matches regex expression "*"
        stubFor(get(urlPathEqualTo("/regexAll"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:987654321@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveIncomingCalls == 1);
        assertTrue(liveOutgoingCalls == 1);
        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/regexAll")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: " + liveCalls);
        liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: " + liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        assertTrue(maxConcurrentCalls == 2);
        assertTrue(maxConcurrentIncomingCalls == 1);
        assertTrue(maxConcurrentOutgoingCalls == 1);
    }

    static String ussdClientRequestBody = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<ussd-data>\n"
            + "\t<language value=\"en\"/>\n"
            + "\t<ussd-string value=\"5544\"/>\n"
            + "</ussd-data>";

    static String ussdRestcommResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<ussd-data>\n"
            + "<language value=\"en\"></language>\n"
            + "<ussd-string value=\"The information you requested is 1234567890\"></ussd-string>\n"
            + "<anyExt>\n"
            + "<message-type>processUnstructuredSSRequest_Response</message-type>\n"
            + "</anyExt>\n"
            + "</ussd-data>\n";

    private String ussdRcml = "<Response><UssdMessage>The information you requested is 1234567890</UssdMessage></Response>";
    @Test
    public void testUssdPull() {

        stubFor(get(urlPathEqualTo("/ussd"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(ussdRcml)));


        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.initiateOutgoingCall(aliceContact, "sip:*777#@" + restcommContact, null, ussdClientRequestBody, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(aliceCall);
        //aliceCall.waitForAuthorisation(30 * 1000);

        assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = aliceCall.getLastReceivedResponse().getStatusCode();
        if (responseBob == Response.TRYING) {
            assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));
            assertTrue(aliceCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        } else {
            assertTrue(aliceCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        }

        assertTrue(aliceCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, aliceCall.getLastReceivedResponse().getStatusCode());
        assertTrue(aliceCall.sendInviteOkAck());

        assertTrue(aliceCall.getDialog().getState().getValue()== DialogState._CONFIRMED);

        assertTrue(aliceCall.listenForDisconnect());

        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        aliceCall.respondToDisconnect();
        SipRequest bye = aliceCall.getLastReceivedRequest();
        String receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(ussdRestcommResponse.trim()));
        aliceCall.dispose();

    }


    @Deployment(name = "DialAction", managed = true, testable = false)
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
        replacements.put("5093", String.valueOf(subAccountPort));         
        List<String> resources = new ArrayList(Arrays.asList("dial-client-entry_wActionUrl.xml"));
        return WebArchiveUtil.createWebArchiveNoGw("restcomm_callRegex.xml", 
                "restcomm.script_callRegexTest",resources, replacements);
    }     

}

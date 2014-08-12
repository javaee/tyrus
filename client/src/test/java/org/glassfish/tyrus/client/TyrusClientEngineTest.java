/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.tyrus.client;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;

import org.glassfish.tyrus.client.auth.Credentials;
import org.glassfish.tyrus.core.Base64Utils;
import org.glassfish.tyrus.core.HandshakeException;
import org.glassfish.tyrus.core.TyrusEndpointWrapper;
import org.glassfish.tyrus.core.l10n.LocalizationMessages;
import org.glassfish.tyrus.spi.ClientEngine;
import org.glassfish.tyrus.spi.UpgradeRequest;
import org.glassfish.tyrus.spi.UpgradeResponse;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Ondrej Kosatka (ondrej.kosatka at oracle.com)
 */
public class TyrusClientEngineTest {

    public static final String ENDPOINT_URI_HTTP = "http://localhost/echo";
    public static final String ENDPOINT_URI_WS = "ws://localhost/echo";
    public static final String ENDPOINT_URI_HTTPS = "https://localhost/echo";
    public static final String ENDPOINT_URI_WSS = "wss://localhost/echo";
    public static final String ENDPOINT_URI_HTTP_PORT = "http://localhost:80/echo";
    public static final String ENDPOINT_URI_WS_PORT = "ws://localhost:80/echo";
    public static final String ENDPOINT_URI_HTTPS_PORT = "https://localhost:443/echo";
    public static final String ENDPOINT_URI_WSS_PORT = "wss://localhost:443/echo";

    @Test
    public void testBasicFlow() throws DeploymentException, HandshakeException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getUpgradeResponse(generateServerKey(secWebsocketKey)), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);
    }

    @Test
    public void testErrorFlow1() throws DeploymentException, HandshakeException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        engine.processError(new Exception());

        try {
            engine.createUpgradeRequest(null);
            fail("createUpgradeRequest after processError must fail.");
        } catch (IllegalStateException e) {
            // ok
        }
    }

    @Test
    public void testErrorFlow2() throws DeploymentException, HandshakeException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        engine.processError(new Exception());

        try {
            engine.processResponse(null, null, null);
            fail("processResponse after processError must fail.");
        } catch (IllegalStateException e) {
            // ok
        }
    }

    @Test
    public void testErrorFlow3() throws DeploymentException, HandshakeException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getUpgradeResponse(generateServerKey(secWebsocketKey)), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);

        try {
            engine.processError(new Exception());
            fail("processError after ClientEngine.ClientUpgradeStatus.SUCCESS must fail.");
        } catch (IllegalStateException e) {
            // ok
        }
    }

    @Test
    public void testAuthFlow() throws DeploymentException, HandshakeException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ClientProperties.CREDENTIALS, new Credentials("username", "password"));
        ClientEngine engine = getClientEngine(properties);

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getAuthenticateResponse(), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        clientUpgradeInfo = engine.processResponse(getUpgradeResponse(generateServerKey(secWebsocketKey)), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);
    }

    @Test
    public void testRedirectFlow() throws DeploymentException, HandshakeException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ClientProperties.REDIRECT_ENABLED, true);
        ClientEngine engine = getClientEngine(properties);

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getRedirectionsResponse(ENDPOINT_URI_WS), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        clientUpgradeInfo = engine.processResponse(getUpgradeResponse(generateServerKey(secWebsocketKey)), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);
    }

    @Test
    public void testRetryAfterFlow() throws DeploymentException, HandshakeException {
        Map<String, Object> properties = new HashMap<String, Object>();
        ClientEngine engine = getClientEngine(properties);

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getRetryAfterResponse("20"), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.UPGRADE_REQUEST_FAILED);

        properties = new HashMap<String, Object>();
        engine = getClientEngine(properties);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        clientUpgradeInfo = engine.processResponse(getUpgradeResponse(generateServerKey(secWebsocketKey)), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);
    }

    @Test
    public void testRedirectAndAuthFlow() throws DeploymentException, HandshakeException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ClientProperties.REDIRECT_ENABLED, true);
        properties.put(ClientProperties.CREDENTIALS, new Credentials("username", "password"));
        ClientEngine engine = getClientEngine(properties);

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getRedirectionsResponse(ENDPOINT_URI_WS), null, null);
        assertTrue("Another request should be required", clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        clientUpgradeInfo = engine.processResponse(getAuthenticateResponse(), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        clientUpgradeInfo = engine.processResponse(getUpgradeResponse(generateServerKey(secWebsocketKey)), null, null);
        assertTrue("Another request should be required", clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);
    }

    @Test
    public void testAuthAndRedirectFlow() throws DeploymentException, HandshakeException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ClientProperties.REDIRECT_ENABLED, true);
        properties.put(ClientProperties.CREDENTIALS, new Credentials("username", "password"));
        ClientEngine engine = getClientEngine(properties);

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getAuthenticateResponse(), null, null);
        assertTrue(clientUpgradeInfo.getUpgradeStatus().toString(), clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        clientUpgradeInfo = engine.processResponse(getRedirectionsResponse(ENDPOINT_URI_WS), null, null);
        assertTrue("Another request should be required", clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        clientUpgradeInfo = engine.processResponse(getUpgradeResponse(generateServerKey(secWebsocketKey)), null, null);
        assertTrue("Another request should be required", clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);
    }

    @Test
    public void testFlowReponse200() throws DeploymentException, HandshakeException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("", upgradeRequest);

        ClientEngine.ClientUpgradeInfo clientUpgradeInfo = engine.processResponse(getUpgradeResponse(200, Collections.<String, List<String>>emptyMap()), null, null);
        assertTrue("processResponse(..) must fail", clientUpgradeInfo.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.UPGRADE_REQUEST_FAILED);
    }

    @Test
    public void testCallCreateRequestTwice() throws DeploymentException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("First call must return instance of UpgradeRequest", upgradeRequest);
        try {
            engine.createUpgradeRequest(null);
            fail("Second call of createUpgradeRequest must fail");
        } catch (IllegalStateException e) {
            // ok
        }
    }

    @Test
    public void testCallProcessResponseTwice() throws DeploymentException, HandshakeException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        String secWebsocketKey = upgradeRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);

        UpgradeResponse upgradeResponse = getUpgradeResponse(generateServerKey(secWebsocketKey));
        ClientEngine.ClientUpgradeInfo info = engine.processResponse(upgradeResponse, null, null);
        assertTrue("First call should succeed", info.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.SUCCESS);
        try {
            engine.processResponse(upgradeResponse, null, null);
            fail("Second call of createUpgradeRequest must fail");
        } catch (IllegalStateException e) {
            // ok
        }
    }

    @Test
    public void testCallProcessResponseFirst() throws DeploymentException {
        ClientEngine engine = getClientEngine(Collections.<String, Object>emptyMap());

        try {
            engine.processResponse(getUpgradeResponse(""), null, null);
            fail("Second call of createUpgradeRequest must fail");
        } catch (IllegalStateException e) {
            // ok
        }
    }

    @Test
    public void testTrasformLocationHttpToWsWithDefaultPorts() throws DeploymentException, HandshakeException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ClientProperties.REDIRECT_ENABLED, true);
        ClientEngine engine = getClientEngine(ENDPOINT_URI_WS, properties);

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        UpgradeResponse upgradeResponse = getRedirectionsResponse(ENDPOINT_URI_HTTP);
        ClientEngine.ClientUpgradeInfo info = engine.processResponse(upgradeResponse, null, null);
        assertTrue("Must be redirected", info.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);
        assertEquals("Redirected request URI is wrong", ENDPOINT_URI_WS_PORT, upgradeRequest.getRequestUri());

        upgradeResponse = getRedirectionsResponse(ENDPOINT_URI_HTTPS);
        info = engine.processResponse(upgradeResponse, null, null);
        assertTrue("Must be redirected", info.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);
        assertEquals("Redirected request URI is wrong", ENDPOINT_URI_WSS_PORT, upgradeRequest.getRequestUri());

        upgradeResponse = getRedirectionsResponse(ENDPOINT_URI_WSS);
        info = engine.processResponse(upgradeResponse, null, null);
        assertTrue("It must failed - wss://localhost/echo is the same uri as https://localhost/echo (both should be transformed into wss://localhost:443/echo)",
                info.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.UPGRADE_REQUEST_FAILED);
    }

    @Test
    public void testTrasformLocationHttpToWs() throws DeploymentException, HandshakeException {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ClientProperties.REDIRECT_ENABLED, true);
        ClientEngine engine = getClientEngine(ENDPOINT_URI_WS, properties);

        UpgradeRequest upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);

        UpgradeResponse upgradeResponse = getRedirectionsResponse(ENDPOINT_URI_HTTP_PORT);
        ClientEngine.ClientUpgradeInfo info = engine.processResponse(upgradeResponse, null, null);
        assertTrue("Must be redirected", info.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);
        assertEquals("Redirected request URI is wrong", ENDPOINT_URI_WS_PORT, upgradeRequest.getRequestUri());

        upgradeResponse = getRedirectionsResponse(ENDPOINT_URI_HTTPS_PORT);
        info = engine.processResponse(upgradeResponse, null, null);
        assertTrue("Must be redirected", info.getUpgradeStatus() == ClientEngine.ClientUpgradeStatus.ANOTHER_UPGRADE_REQUEST_REQUIRED);

        upgradeRequest = engine.createUpgradeRequest(null);
        assertNotNull("We must get UpgradeRequest instance", upgradeRequest);
        assertEquals("Redirected request URI is wrong", ENDPOINT_URI_WSS_PORT, upgradeRequest.getRequestUri());
    }


    private UpgradeResponse getUpgradeResponse(final String serverKey) {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put(UpgradeRequest.CONNECTION, Collections.singletonList(UpgradeRequest.UPGRADE));
        headers.put(UpgradeRequest.UPGRADE, Collections.singletonList(UpgradeRequest.WEBSOCKET));

        return getUpgradeResponse(101, headers, serverKey);
    }

    private UpgradeResponse getAuthenticateResponse() {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put(UpgradeResponse.WWW_AUTHENTICATE, Collections.singletonList("Basic realm=test"));

        return getUpgradeResponse(401, headers);
    }

    private UpgradeResponse getRedirectionsResponse(final String requestUri) {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put(UpgradeResponse.LOCATION, Collections.singletonList(requestUri));

        return getUpgradeResponse(301, headers);
    }

    private UpgradeResponse getRetryAfterResponse(final String retryAfter) {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put(UpgradeResponse.RETRY_AFTER, Collections.singletonList(retryAfter));

        return getUpgradeResponse(503, headers);
    }

    private UpgradeResponse getUpgradeResponse(final int statusCode, final Map<String, List<String>> headers, final String serverKey) {
        return new UpgradeResponse() {
            @Override
            public int getStatus() {
                return statusCode;
            }

            @Override
            public void setStatus(int status) {

            }

            @Override
            public void setReasonPhrase(String reason) {

            }

            @Override
            public Map<String, List<String>> getHeaders() {
                headers.put(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, Collections.singletonList(serverKey));
                return headers;
            }
        };
    }

    private UpgradeResponse getUpgradeResponse(final int statusCode, final Map<String, List<String>> headers) {
        return new UpgradeResponse() {
            @Override
            public int getStatus() {
                return statusCode;
            }

            @Override
            public void setStatus(int status) {

            }

            @Override
            public void setReasonPhrase(String reason) {

            }

            @Override
            public Map<String, List<String>> getHeaders() {
                return headers;
            }
        };
    }

    private ClientEngine getClientEngine(final Map<String, Object> properties) throws DeploymentException {
        return getClientEngine(ENDPOINT_URI_WS, properties);
    }

    private ClientEngine getClientEngine(final String requestUri, final Map<String, Object> properties) throws DeploymentException {
        Endpoint endpoint = new TestEndpoint();
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        TyrusEndpointWrapper endpointWrapper = new TyrusEndpointWrapper(endpoint, endpointConfig, null, null, "/path", null, null, null, null);
        return new TyrusClientEngine(endpointWrapper, new TyrusClientEngine.ClientHandshakeListener() {
            @Override
            public void onSessionCreated(Session session) {

            }

            @Override
            public void onError(Throwable exception) {

            }
        }, properties, URI.create(requestUri));
    }

    private static class TestEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig endpointConfig) {

        }
    }

    private String generateServerKey(String clientKey) throws HandshakeException {
        String key = clientKey + UpgradeRequest.SERVER_KEY_HASH;
        final MessageDigest instance;
        try {
            instance = MessageDigest.getInstance("SHA-1");
            instance.update(key.getBytes("UTF-8"));
            final byte[] digest = instance.digest();
            if (digest.length != 20) {
                throw new HandshakeException(LocalizationMessages.SEC_KEY_INVALID_LENGTH(digest.length));
            }

            return Base64Utils.encodeToString(digest, false);
        } catch (NoSuchAlgorithmException e) {
            throw new HandshakeException(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            throw new HandshakeException(e.getMessage());
        }
    }
}

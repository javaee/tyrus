/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.tests.qa.tools;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.glassfish.tyrus.tests.qa.config.AppConfig;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * @author Michal Čonos (michal.conos at oracle.com)
 */
public class CommChannel {

    private static final Logger logger = Logger.getLogger(CommChannel.class.getCanonicalName());
    private static final int defaultListenPort = 8338;
    private static final String defaultScheme = "http";
    private static final String defaultHost = "localhost";
    private int listenPort = defaultListenPort;
    private String scheme = defaultScheme;
    private String host = defaultHost;

    public CommChannel(AppConfig conf) {
        this(conf.getCommScheme(), conf.getCommHost(), conf.getCommPort());
    }

    public CommChannel(String scheme, String host, int port) {
        this.listenPort = port;
        this.scheme = scheme;
        this.host = host;
    }

    public CommChannel() {
    }

    public String getResourcePath(String resource) {
        return "/" + resource + "/";
    }

    public String getResourcePath(String resource, String id) {
        return getResourcePath(resource) + id;
    }

    public URI getResource(String resource) throws URISyntaxException {
        URI uri = new URI(getScheme(), null, getHost(), getListenPort(), getResourcePath(resource), null, null);
        logger.log(Level.FINE, "getResource(): {0}", uri.toString());
        return uri;
    }

    public URI getResourceById(String resource, String id) throws URISyntaxException {
        URI uri = new URI(getScheme(), null, getHost(), getListenPort(), getResourcePath(resource, id), null, null);
        logger.log(Level.FINE, "getResource(): {0}", uri.toString());
        return uri;
    }

    public URI setStatusURI(String resource, String id, String status) throws URISyntaxException {
        URI uri = new URI(getScheme(), null, getHost(), getListenPort(), getResourcePath(resource), "id=" + id + "&status=" + status, null);
        logger.log(Level.FINE, "getResource(): {0}", uri.toString());
        return uri;
    }

    public int getListenPort() {
        return listenPort;
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    private static Map<String, String> sessions = new ConcurrentHashMap<String, String>();

    public class Server {

        private org.eclipse.jetty.server.Server server;

        private String sessionToJSON(String id) {
            String response = "";
            String status = sessions.get(id);
            try {
                response = new JSONStringer()
                        .object()
                        .key("resource")
                        .value("session")
                        .key("status")
                        .value(status)
                        .key("session")
                        .value(id)
                        .endObject()
                        .toString();
            } catch (JSONException ex) {
                ex.printStackTrace();
                throw new RuntimeException(ex.getCause());
            }
            return response;
        }

        public void destroy() throws Exception {
            if (server != null) {
                server.stop();
                server.destroy();
                logger.log(Level.INFO, "Jetty Destroyed");
            }
        }

        public void start() throws Exception {
            start(getListenPort());
            logger.log(Level.INFO, "Jetty Started");
        }

        private boolean isCreateNewResource(String ctx, String resource) {
            String id = getResourceId(ctx, resource);
            return id.isEmpty();
        }

        private boolean isCreateNewSession(String ctx) {
            return isCreateNewResource(ctx, "sessions");
        }

        private boolean isResourceRequest(String ctx, String resource) {
            return ctx.startsWith(getResourcePath(resource));
        }

        private boolean isSessionRequest(String ctx) {
            return isResourceRequest(ctx, "sessions");
        }

        private String getSessionId(String ctx) {
            return getResourceId(ctx, "sessions");
        }

        private String getResourceId(String ctx, String resource) {
            String id = ctx.replaceFirst("^" + getResourcePath(resource), "");
            id = id.replaceFirst("^/", "");
            id = id.replaceFirst("/$", "");
            logger.log(Level.FINE, "id={0}", id);
            return id;
        }

        public void start(final int port) throws InterruptedException {
            new Thread() {
                @Override
                public void run() {
                    try {
                        startJetty(port);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        logger.log(Level.INFO, "Can't start the server:{0}", ex.getMessage());
                    }
                }
            }.start();
            Thread.sleep(1000);
        }

        public void stop() throws Exception {
            if (server != null) {
                server.stop();
            }
        }

        private void startJetty(int port) throws Exception {
            server = new org.eclipse.jetty.server.Server(port);
            server.setHandler(new AbstractHandler() {
                @Override
                public synchronized void handle(String target, Request request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException, ServletException {
                    response.setContentType("text/html;charset=utf-8");

                    String path = httpRequest.getPathInfo();
                    logger.log(Level.FINE, "ctxPath={0}", path);

                    if (isSessionRequest(path)) {
                        // Session handling is here
                        if (!isCreateNewSession(path)) {
                            // get session status
                            String id = getSessionId(path);
                            if (sessions.containsKey(id)) {
                                response.getWriter().println(sessionToJSON(id));
                                response.setStatus(HttpServletResponse.SC_OK);
                                request.setHandled(true);
                                return;
                            }
                        } else {
                            // create session
                            String sessionId = httpRequest.getParameter("id");
                            String sessionStatus = httpRequest.getParameter("status");
                            logger.log(Level.FINE, "create: id={0} status={1}", new Object[]{sessionId, sessionStatus});
                            if (sessionId != null) {
                                sessions.put(sessionId, (sessionStatus != null) ? sessionStatus : "null");
                                response.setStatus(HttpServletResponse.SC_OK);
                                request.setHandled(true);
                                return;
                            }
                        }
                    }
                }
            });
            server.start();
            server.join();
        }
    }

    public class Client {

        public ClientResponse getResourceStatus(String resource, String id) throws URISyntaxException {
            logger.log(Level.INFO, "getResourceStatus: {0} {1}", new Object[]{resource, id});
            WebResource web = com.sun.jersey.api.client.Client.create()
                    .resource(getResourceById(resource, id));
            ClientResponse response = web.get(ClientResponse.class);
            return response;
        }

        public ClientResponse setResourceStatus(String resource, String id, String status) throws URISyntaxException {
            logger.log(Level.INFO, "setResourceStatus: {0} {1}={2}", new Object[]{resource, id, status});
            WebResource web = com.sun.jersey.api.client.Client.create()
                    .resource(setStatusURI(resource, id, status));
            ClientResponse response = web.get(ClientResponse.class);
            return response;
        }

        private String handleResource(ClientResponse response, String key) throws JSONException {
            switch (response.getStatus()) {
                case 200:
                    String jsonText = response.getEntity(String.class);
                    logger.log(Level.INFO, "handleResource: get {0}", new JSONObject(jsonText).getString(key));
                    return new JSONObject(jsonText).getString(key);
                case 404:
                    return "null";
                default:
                    throw new RuntimeException("ClientResponse not 200 OK for :" + key + " : " + response.getStatus());
            }
        }


        public String getSessionStatus(String id) throws URISyntaxException, JSONException {
            return handleResource(getResourceStatus("sessions", id), "status");
        }

        public void setSessionStatus(String id, String status) {
            try {
                ClientResponse response = setResourceStatus("sessions", id, status);
                if (response.getStatus() != 200) {
                    throw new RuntimeException("ClientReponse status not 200 OK:" + response.getStatus());
                }
            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, "setSessionStatus:{0}", ex.getMessage());
            }
        }
    }
}

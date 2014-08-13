/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.tyrus.core.uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.core.DebugContext;
import org.glassfish.tyrus.core.TyrusEndpointWrapper;
import org.glassfish.tyrus.core.uri.internal.PathSegment;
import org.glassfish.tyrus.core.uri.internal.UriComponent;

/**
 * Defines a match on an endpoint. The match is either exact, or is not exact.
 * If the match is not exact, it means that some of the path segments on the endpoint
 * are variables. In this case, the Match object carries the indices of the variable
 * segments in the endpoint path, the names and the values.
 *
 * @author dannycoward
 */
public class Match {

    private final TyrusEndpointWrapper endpointWrapper; // the endpoint that has the path
    private final List<String> parameterNames = new ArrayList<String>();
    private final List<String> parameterValues = new ArrayList<String>();
    //list of all segment indices in the path with variables
    private final List<Integer> variableSegmentIndices = new ArrayList<Integer>();

    private static final Logger LOGGER = Logger.getLogger(Match.class.getName());
    private static final boolean noisy = false;

    private static void debug(String message) {
        if (noisy) {
            LOGGER.log(Level.INFO, message);
        }
    }

    /**
     * Constructor.
     *
     * @param endpointWrapper {@link TyrusEndpointWrapper} instance.
     */
    private Match(TyrusEndpointWrapper endpointWrapper) {
        this.endpointWrapper = endpointWrapper;
    }

    /**
     * Get path.
     *
     * @return path.
     */
    public String getPath() {
        return this.endpointWrapper.getEndpointPath();
    }

    /**
     * Get variable segment indices (indexes).
     *
     * @return list of indices.
     */
    public List<Integer> getVariableSegmentIndices() {
        return this.variableSegmentIndices;
    }

    int getLowestVariableSegmentIndex() {
        if (this.getVariableSegmentIndices().isEmpty()) {
            return -1;
        } else {
            return this.getVariableSegmentIndices().get(0);
        }
    }

    /**
     * Add new parameter.
     *
     * @param name  parameter name.
     * @param value parameter value.
     * @param index parameter index.
     */
    void addParameter(String name, String value, int index) {
        this.parameterNames.add(name);
        this.parameterValues.add(value);
        this.variableSegmentIndices.add(index);
    }

    /**
     * Get parameter names.
     *
     * @return list of parameter names.
     */
    public List<String> getParameterNames() {
        return this.parameterNames;
    }

    /**
     * Get value of given parameter.
     *
     * @param name parameter name.
     * @return value of given parameter.
     */
    public String getParameterValue(String name) {
        return this.parameterValues.get(this.parameterNames.indexOf(name));
    }

    /**
     * Get {@link TyrusEndpointWrapper}.
     *
     * @return web socket application of this {@link Math}.
     */
    public TyrusEndpointWrapper getEndpointWrapper() {
        return this.endpointWrapper;
    }

    @Override
    public String toString() {
        return endpointWrapper.getEndpointPath();
    }

    private String paramsToString() {
        StringBuilder sb = new StringBuilder();
        for (String nextName : this.parameterNames) {
            sb.append(nextName).append("=").append(this.getParameterValue(nextName));
            if (this.parameterNames.indexOf(nextName) != this.parameterNames.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    /**
     * TODO.
     *
     * @return TODO.
     */
    public boolean isExact() {
        return this.getLowestVariableSegmentIndex() == -1;
    }

    /**
     * Returns null, or a Match object
     *
     * @param incoming       TODO
     * @param thingsWithPath TODO
     * @return TODO
     */
    public static Match getBestMatch(String incoming, Set<TyrusEndpointWrapper> thingsWithPath) {
        List<Match> sortedMatches = getAllMatches(incoming, thingsWithPath, new DebugContext());
        if (sortedMatches.isEmpty()) {
            return null;
        } else {
            return sortedMatches.get(0);
        }
    }

    /**
     * Return a list of all endpoints with path matching the request path. The endpoints are in order of match preference, best match first.
     *
     * @param incoming       request path.
     * @param thingsWithPath endpoints.
     * @param debugContext   debug context.
     * @return a list of all endpoints with path matching the request path. The endpoints are in order of match preference, best match first.
     */
    public static List<Match> getAllMatches(String incoming, Set<TyrusEndpointWrapper> thingsWithPath, DebugContext debugContext) {
        Set<Match> matches = new HashSet<Match>();
        for (TyrusEndpointWrapper nextThingWithPath : thingsWithPath) {
            Match m = matchPath(incoming, nextThingWithPath, debugContext);
            if (m != null) {
                matches.add(m);
            }
        }
        List<Match> sortedMatches = new ArrayList<Match>();
        sortedMatches.addAll(matches);
        Collections.sort(sortedMatches, new MatchComparator(debugContext));
        debugContext.appendTraceMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "Endpoints matched to the request URI: ", sortedMatches);
        return sortedMatches;
    }

    private static List<String> asEquivalenceList(String path) {
        List<String> eList = new ArrayList<String>();
        List<PathSegment> asSegments = UriComponent.decodePath(path, true);
        for (PathSegment next : asSegments) {
            if (isVariable(next.getPath())) {
                eList.add("{x}");
            } else {
                eList.add(next.getPath());
            }
        }
        return eList;
    }

    /**
     * Check for equivalent path.
     *
     * @param paths list of paths.
     * @return {@code true} if at least one path in given list is equivalent with current one, {@code false} otherwise.
     */
    public static boolean checkForEquivalents(List<String> paths) {

        for (int i = 0; i < paths.size(); i++) {
            String nextPath = paths.get(i);
            for (int j = 0; j < paths.size(); j++) {
                if (j != i) {
                    if (isEquivalent(nextPath, paths.get(j))) {
                        debug("two the same!!: " + nextPath + " is equivalent to " + paths.get(j));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check for equivalent paths.
     *
     * @param path1 path to be checked.
     * @param path2 path to be checked.
     * @return {@code true} when provided path are equivalent, {@code false} otherwise.
     */
    public static boolean isEquivalent(String path1, String path2) {
        debug("isEquivalent ? " + path1 + " and " + path2);
        List<String> path1EList = asEquivalenceList(path1);
        List<String> path2EList = asEquivalenceList(path2);
        boolean eq = path1EList.equals(path2EList);
        debug("isEquivalent ? " + eq);
        return eq;
    }

    private static Match matchPath(String incoming, TyrusEndpointWrapper hasPath, DebugContext debugContext) {
        debugContext.appendTraceMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "Matching request URI ", incoming, " against ", hasPath.getEndpointPath());
        List<PathSegment> incomingList = UriComponent.decodePath(incoming, true);
        List<PathSegment> pathList = UriComponent.decodePath(hasPath.getEndpointPath(), true);
        if (incomingList.size() != pathList.size()) {
            debugContext.appendTraceMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "URIs ", incoming, " and ", hasPath.getEndpointPath(), " have different length");
            return null;
        } else {
            Match m = new Match(hasPath);
            boolean somethingMatched = false;
            for (int i = 0; i < incomingList.size(); i++) {
                String incomingSegment = incomingList.get(i).getPath();
                String pathSegment = pathList.get(i).getPath();
                if (incomingSegment.equals(pathSegment)) {
                    somethingMatched = true;
                    // continue...
                } else if (isVariable(pathSegment)) {
                    somethingMatched = true;
                    m.addParameter(getVariableName(pathSegment), incomingSegment, i);
                } else {
                    debugContext.appendTraceMessage(LOGGER, Level.FINE, DebugContext.Type.MESSAGE_IN, "Segment \"", pathSegment, "\" does not match");
                    return null; // no match
                }
            }
            if (somethingMatched) {
                return m;
            } else {
                return null;
            }

        }
    }

    private static boolean isVariable(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private static String getVariableName(String segment) {
        return segment.substring(1, segment.length() - 1);
    }
}


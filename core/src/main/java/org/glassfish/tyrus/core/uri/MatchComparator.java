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

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.core.DebugContext;

/**
 * The comparator is used to order the best matches in a list.
 *
 * @author dannycoward
 */
class MatchComparator implements Comparator<Match>, Serializable {

    private Logger LOGGER = Logger.getLogger(MatchComparator.class.getName());
    private DebugContext debugContext;

    MatchComparator(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    // m1 wins = return -1
    // m2 wins = return 1
    // neither wins = return 0
    @Override
    public int compare(Match m1, Match m2) {
        debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, "Choosing better match from ", m1, " and ", m2);
        boolean m1exact = m1.isExact();
        boolean m2exact = m2.isExact();

        if (m1exact) {
            if (m2exact) { // both exact matches, no-one wins
                debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, "Both ", m1, " and ", m2, " are exact matches");
                return 0;
            } else { // m2not exact, m1 is, m1 wins
                debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, m1, " is an exact match");
                // m1 is exact match
                return -1; // m1 wins
            }
        } else { // m1 is not exact, m2 is, m2 wins
            if (m2exact) {
                debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, m2, " is an exact match");
                //m 2 is exact match
                return 1; //m2 is exact, m1 isn't, so m2 wins
            } else { // neither are exact !
                // iterate through the variable segment indices, left to right
                // test each one: the one with the larger index wins since
                // more of the path from the left is exact.
                // if the two indices are the same, keep going
                // if all the indices are the same, they are equivalent
                // if the same, keep going.
                List<Integer> m1Indices = m1.getVariableSegmentIndices();
                List<Integer> m2Indices = m2.getVariableSegmentIndices();

                for (int i = 0; i < Math.max(m1Indices.size(), m2Indices.size()); i++) {

                    if (i > m2Indices.size() - 1) {
                        debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, m2, " is a  better match, because ", m1, " has more variables");
                        //m2 wins because m1 has more variables to go.
                        return 1;
                    } else if (i > m1Indices.size() - 1) {
                        debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, m1, " is a  better match, because ", m2, " has more variables");
                        // m1 wins because m2 has more variables to go
                        return -1; // m1 wins because m2 has more variables to go
                    } else {
                        int m1Index = m1Indices.get(i);
                        int m2Index = m2Indices.get(i);
                        if (m1Index > m2Index) {
                            // m1 wins as it has a larger exact path
                            debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, m1, " is a  better match, because it has longer exact path");
                            return -1;
                        } else if (m2Index > m1Index) {
                            // m2 wins as it has a larger exact path
                            debugContext.appendTraceMessage(LOGGER, Level.FINER, DebugContext.Type.MESSAGE_IN, m2, " is a  better match, because it has longer exact path");
                            return 1;
                        }
                    }
                }
                // both had same indices
                return 0;
            }
        }
        // can't get here
    }
}
/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import org.glassfish.tyrus.tests.qa.regression.Issue;

/**
 * @author michal.conos at oracle.com
 */
public class IssueManipulator {

    private static final Map<Issue.IssueId, Issue> knownIssues = new EnumMap<Issue.IssueId, Issue>(Issue.IssueId.class);

    static {
        knownIssues.put(
                Issue.IssueId.TYRUS_93,
                new Issue(Issue.IssueId.TYRUS_93, "ClientEndpoint session.getRequestURI()==null"));
        knownIssues.put(
                Issue.IssueId.TYRUS_94,
                new Issue(Issue.IssueId.TYRUS_94, "ServerEndPoint: onError(): throwable.getCause()==null"));
        knownIssues.put(
                Issue.IssueId.TYRUS_101, new Issue(Issue.IssueId.TYRUS_101,
                                                   "CloseReason not propagated to server side (when close() " +
                                                           "initiated from client)"));
        knownIssues.put(
                Issue.IssueId.TYRUS_104, new Issue(Issue.IssueId.TYRUS_104,
                                                   "session should raise IllegalStateException when Session" +
                                                           ".getRemote() called on a closed session"));
    }

    private static final Logger logger = Logger.getLogger(IssueManipulator.class.getCanonicalName());

    private IssueManipulator() {
    }

    public static Issue getIssueById(Issue.IssueId id) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (knownIssues.containsKey(id)) {
            return knownIssues.get(id);
        }
        throw new RuntimeException(String.format("Cannot find the issue: %s!", id));

    }

    public static boolean isIssueEnabled(Issue.IssueId id) {
        Issue loadedIssue = loadIssue(getIssueById(id));
        return loadedIssue.isEnabled();
    }

    private static String getIssueHolder(Issue issue) {
        return Misc.separatorsToUnix(Misc.getTempDirectoryPath() + "/" + issue.getIssueId().toString());
    }

    public static void saveIssue(Issue issue) {
        if (issue == null) {
            throw new IllegalArgumentException("issue is null!");
        }
        logger.log(Level.FINE,
                   String.format("saveIssue(): Saving issue:%s [%s]", getIssueHolder(issue), issue.isEnabled()));
        SerializationToolkit stool = new SerializationToolkit(getIssueHolder(issue));
        stool.save(issue);
    }

    public static Issue loadIssue(Issue issue) {
        if (issue == null) {
            throw new IllegalArgumentException("issue is null!");
        }
        SerializationToolkit stool = new SerializationToolkit(getIssueHolder(issue));
        Object obj = stool.load();
        if (obj != null && obj instanceof Issue) {
            Issue retrivedIssue = (Issue) obj;
            logger.log(Level.FINE, String.format("loadIssue(): Loading issue:%s [%s]", getIssueHolder(issue),
                                                 retrivedIssue.isEnabled()));
            return retrivedIssue;
        }

        throw new RuntimeException("loaded object is not Issue");
    }

    /**
     * Disable all issue but the on requested. Handy for regression testing
     */
    public static void disableAllButThisOne(Issue issue) {
        disableAll();
        issue.enable();
        saveIssue(issue);
    }

    /**
     * Disable all issue but the on requested. Handy for regression testing
     */
    public static void disableAllButThisOne(Issue.IssueId id) {
        disableAll();
        Issue issue = getIssueById(id);
        issue.enable();
        saveIssue(issue);
    }

    /**
     * Enable All issues in the database
     */
    public static void enableAll() {
        for (Issue crno : knownIssues.values()) {
            crno.enable();
            saveIssue(crno);
        }
    }

    /**
     * Disable all issue in the database
     */
    public static void disableAll() {
        for (Issue crno : knownIssues.values()) {
            crno.disable();
            saveIssue(crno);
        }
    }

}

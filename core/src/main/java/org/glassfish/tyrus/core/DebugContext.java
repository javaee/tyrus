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

package org.glassfish.tyrus.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.tyrus.spi.UpgradeResponse;

/**
 * A {@link java.util.logging.Logger} wrapper that gives logging records a common formatting and temporarily stores log
 * records and postpones their logging until they can be provided with a session ID. After a session ID has been provided,
 * messages are logged immediately.
 * <p/>
 * Log records are provided with a session ID, so that log records from a single upgrade request can be easily linked
 * together in a log of a busy server or client.
 *
 * @author Petr Janouch (petr.janouch at oracle.com)
 */
public class DebugContext {

    // is not thread safe - it is assumed that it will be used only in the "handshake phase"
    private List<LogRecord> logRecords = new ArrayList<LogRecord>();
    // is not thread safe - it is assumed that it will be used only in the "handshake phase"
    private Map<String, List<String>> tracingHeaders = new HashMap<String, List<String>>();
    private final long startTimestamp;
    private final Level tracingLevel;
    private String sessionId = null;

    /**
     * Constructor that configures tracing to be ON and accepts tracing threshold as a parameter.
     *
     * @param tracingThreshold tracing threshold.
     */
    public DebugContext(TracingThreshold tracingThreshold) {
        startTimestamp = System.nanoTime();
        if (TracingThreshold.SUMMARY == tracingThreshold) {
            tracingLevel = Level.FINE;
        } else {
            tracingLevel = Level.FINER;
        }
    }

    /**
     * Constructor that configures tracing to be OFF.
     */
    public DebugContext() {
        startTimestamp = System.nanoTime();
        this.tracingLevel = Level.OFF;
    }

    /**
     * Append a message to the log, the logging will be postponed until the message can be provided with a session ID.
     * Randomly generated session ID is used if a session has not been created.
     *
     * @param logger       logger to be used to log the message.
     * @param loggingLevel message level.
     * @param type         type of the message.
     * @param messageParts message parts that will be concatenated to create a log message.
     */
    public void appendLogMessage(Logger logger, Level loggingLevel, Type type, Object... messageParts) {
        appendLogMessageWithThrowable(logger, loggingLevel, type, null, messageParts);
    }

    /**
     * Append a message to the log and to the list of trace messages that are sent in handshake response.
     * The logging will be postponed until the message can be provided with a session ID. Randomly generated session ID
     * is used if a session has not been created.
     *
     * @param logger       logger to be used to log the message.
     * @param loggingLevel message level.
     * @param type         type of the message.
     * @param messageParts message parts that will be stringified and concatenated to create a log message.
     */
    public void appendTraceMessage(Logger logger, Level loggingLevel, Type type, Object... messageParts) {
        appendTraceMessageWithThrowable(logger, loggingLevel, type, null, messageParts);
    }

    /**
     * Append a message to the log, the logging will be postponed until the message can be provided with a session ID.
     * Randomly generated session ID is used if a session has not been created.
     *
     * @param logger       logger to be used to log the message.
     * @param loggingLevel message level.
     * @param type         type of the message.
     * @param t            throwable that has been thrown.
     * @param messageParts message parts that will be stringified and concatenated to create a log message.
     */
    public void appendLogMessageWithThrowable(Logger logger, Level loggingLevel, Type type, Throwable t, Object... messageParts) {
        if (logger.isLoggable(loggingLevel)) {

            String message = stringifyMessageParts(messageParts);

            if (sessionId == null) {
                logRecords.add(new LogRecord(logger, loggingLevel, type, message, t, false));
            } else {
                if (t != null) {
                    logger.log(loggingLevel, formatLogMessage(message, type, System.nanoTime()), t);
                } else {
                    logger.log(loggingLevel, formatLogMessage(message, type, System.nanoTime()));
                }
            }
        }
    }

    /**
     * Append a message to the log and to the list of trace messages that are sent in handshake response.
     * The logging will be postponed until the message can be provided with a session ID. Randomly generated session ID
     * is used if a session has not been created.
     *
     * @param logger       logger to be used to log the message.
     * @param loggingLevel message level.
     * @param type         type of the message.
     * @param t            throwable that has been thrown.
     * @param messageParts message parts that will be stringified and concatenated to create a log message.
     */
    public void appendTraceMessageWithThrowable(Logger logger, Level loggingLevel, Type type, Throwable t, Object... messageParts) {
        if (this.tracingLevel.intValue() <= loggingLevel.intValue()) {
            String message = stringifyMessageParts(messageParts);
            appendTracingHeader(message);
        }

        appendLogMessageWithThrowable(logger, loggingLevel, type, t, messageParts);
    }

    /**
     * Write a message to the standard output, the logging will be postponed until the message can be provided with
     * a session ID. Randomly generated session ID is used if a session has not been created.
     *
     * @param message message to be logged.
     * @param type    type of the message.
     */
    public void appendStandardOutputMessage(Type type, String message) {
        if (sessionId == null) {
            logRecords.add(new LogRecord(null, Level.OFF, type, message, null, true));
        } else {
            System.out.println(formatLogMessage(message, type, System.nanoTime()));
        }
    }

    /**
     * Set a session ID that will be used as a common identifier for logged messages related to the same upgrade request.
     * Setting the session ID will cause the pending messages to be written into the log.
     *
     * @param sessionId session ID.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        flush();
    }

    /**
     * Write pending messages to the log.
     */
    public void flush() {
        if (sessionId == null) {
            // something went wrong before the session could have been initialized, just give all the messages some id.
            sessionId = UUID.randomUUID().toString();
        }

        for (LogRecord logRecord : logRecords) {
            if (logRecord.printToSout) {
                System.out.println(formatLogMessage(logRecord.message, logRecord.type, logRecord.timestamp));
            } else if (logRecord.logger.isLoggable(logRecord.loggingLevel)) {
                if (logRecord.t != null) {
                    logRecord.logger.log(logRecord.loggingLevel, formatLogMessage(logRecord.message, logRecord.type, logRecord.timestamp), logRecord.t);
                } else {
                    logRecord.logger.log(logRecord.loggingLevel, formatLogMessage(logRecord.message, logRecord.type, logRecord.timestamp));
                }
            }
        }

        logRecords.clear();
    }

    /**
     * Get headers containing tracing messages.
     *
     * @return tracing headers.
     */
    public Map<String, List<String>> getTracingHeaders() {
        return tracingHeaders;
    }

    private void appendTracingHeader(String message) {
        String headerName = UpgradeResponse.TRACING_HEADER_PREFIX + String.format("%02d%n", tracingHeaders.size());
        tracingHeaders.put(headerName, Arrays.asList("[" + (System.nanoTime() - startTimestamp) / 1000000 + " ms] " + message));
    }

    private String formatLogMessage(String message, Type type, long timestamp) {
        StringBuilder formattedMessage = new StringBuilder();

        List<String> messageLines = new ArrayList<String>();

        StringTokenizer tokenizer = new StringTokenizer(message, "\n", true);
        while (tokenizer.hasMoreTokens()) {
            messageLines.add(tokenizer.nextToken());
        }

        String prefix;
        if (type == Type.MESSAGE_IN) {
            prefix = "< ";
        } else if (type == Type.MESSAGE_OUT) {
            prefix = "> ";
        } else {
            prefix = "* ";
        }

        boolean isFirst = true;

        for (String line : messageLines) {
            if (isFirst) {
                formattedMessage.append(prefix).append("Session ").append(sessionId).append(" ");
                formattedMessage.append("[").append((timestamp - startTimestamp) / 1000000).append(" ms]: ");
                formattedMessage.append(line);
                isFirst = false;
            } else {
                if (!"\n".equals(line)) {
                    formattedMessage.append(prefix);
                }
                formattedMessage.append(line);
            }
        }

        return formattedMessage.toString();
    }

    private String stringifyMessageParts(Object... messageParts) {
        StringBuilder sb = new StringBuilder();

        for (Object messagePart : messageParts) {
            sb.append(messagePart);
        }

        return sb.toString();
    }

    private static class LogRecord {
        /**
         * Logger that will be used to log the message.
         */
        private Logger logger;
        /**
         * Logger level that will be used when logging the message.
         */
        private Level loggingLevel;
        /**
         * Type of the record - used for graphical purposes when logging.
         */
        private Type type;
        /**
         * Message to be logged.
         */
        private String message;
        /**
         * Throwable to be logged.
         */
        private Throwable t;
        /**
         * {@code true} if the record should be printed into standard output - used if a message should be "logged"
         * even though the logging has been turned off or the configured logging level is too high.
         */
        private boolean printToSout;
        /**
         * Time when a the logged even has occurred.
         */
        private long timestamp;

        LogRecord(Logger logger, Level loggingLevel, Type Type, String message, Throwable t, boolean printToSout) {
            this.logger = logger;
            this.loggingLevel = loggingLevel;
            this.type = Type;
            this.message = message;
            this.t = t;
            this.printToSout = printToSout;
            timestamp = System.nanoTime();
        }
    }

    /**
     * Type of the record - used to graphically distinguish these message types in the log.
     */
    public enum Type {
        MESSAGE_IN,
        MESSAGE_OUT,
        OTHER
    }

    /**
     * Type of tracing - used for tracing configuration.
     */
    public enum TracingType {
        /**
         * No tracing headers will be ever sent in handshake response.
         */
        OFF,
        /**
         * Tracing headers will be sent in handshake response only if X-Tyrus-Tracing-Accept header is present
         * in handshake request.
         */
        ON_DEMAND,
        /**
         * Tracing headers will be present in all handshake responses.
         */
        ALL
    }

    /**
     * Tracing threshold - used for configuration granularity of information that will be sent in tracing headers.
     */
    public enum TracingThreshold {
        /**
         * A less verbose tracing, an equivalent to {@link java.util.logging.Level#FINER} logging level.
         */
        SUMMARY,
        /**
         * A more verbose tracing, an equivalent to {@link java.util.logging.Level#FINE} logging level.
         * <p/>
         * The default tracing threshold.
         */
        TRACE
    }
}

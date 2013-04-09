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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author michal.conos at oracle.com
 */
public class Misc {

    private static final Logger logger = Logger.getLogger(Misc.class.getCanonicalName());

    /**
     * Copy file set to the target directory. If the directory does not exist
     * it is created.
     * @param fileSet set of files to be copied
     * @param dstDirectory  destination where the files are copied
     */
    public static void copyFiles(Set<File> fileSet, File dstDirectory, String regex, String move) throws IOException {
        if(!dstDirectory.isDirectory()) {
            FileUtils.forceMkdir(dstDirectory);
        }
        for(File src: fileSet) {
            File srcParent = src.getParentFile();
            String targetDir = srcParent.toString();
            if(regex!=null) {
                if(move==null) {
                    move = "";
                }
                targetDir = targetDir.replaceFirst(regex, move);
            }
            File dst = new File(dstDirectory, targetDir);
            logger.log(Level.INFO, "copyFiles: {0} ---> {1}", new Object[] {src, dst});
            FileUtils.copyFileToDirectory(src, dst);
        }
    }
    
    


    public static String getTempDirectoryPath() {
        return System.getProperty("java.io.tmpdir");
    }

    public static File getTempDirectory() {
        return new File(getTempDirectoryPath());
    }

    public static void delete(final File path, final long timeout) throws InterruptedException {
        final CountDownLatch timer = new CountDownLatch(1);
        Thread worker = new Thread() {
            @Override
            public void run() {
                try {
                    for (;;) {
                        if (path.delete()) {
                            timer.countDown();
                            break;
                        } else {
                            logger.log(Level.SEVERE, "Delete did not succeded for {0}", path.toString());
                            Thread.sleep(timeout * 10); // 100 tries
                        }


                    }
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        };
        worker.start();
        timer.await(timeout, TimeUnit.SECONDS);
        if(timer.getCount()>0) {
            worker.interrupt();
            throw new RuntimeException(String.format("Delete of %s failed after %d secs!", path.toString(), timeout));
        }
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.test.scripts;

import static org.wildfly.test.util.Environment.NEW_LINE;
import static org.wildfly.test.util.Environment.WILDFLY_HOME;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.test.util.Directories;
import org.wildfly.test.util.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ScriptPathsTestCase {

    private final List<String> defaultPathNames = Arrays.asList(
            "wildfly spaced",
            "wildfly double  spaced",
            "bi\u00dfchen-dir",
            "ni\u00f1o-dir",
            "wildfly%home",
            "wildfly#home",
            "wildfly$home",
            "wildfly@home",
            "wildfly(home)",
            "wildfly!home",
            "wildfly^home",
            "wildfly=home",
            "wildfly'home"
    );

    /*
     * Invalid Windows path characters per http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx#Naming_Conventions
     * The following reserved characters:
     *  < (less than)
     *  > (greater than)
     *  : (colon)
     *  " (double quote)
     *  / (forward slash)
     *  \ (backslash)
     *  | (vertical bar or pipe)
     *  ? (question mark)
     *  * (asterisk)
     */

    private final List<String> linuxPathNames = Arrays.asList(
            "wildfly?home",
            "wildfly\\home",
            "wildfly<home",
            "wildfly>home",
            "wildfly|path",
            "\"wildfly-home\""
    );

    @Test
    public void testDomainPaths() throws Exception {
        testPaths(ServerType.DOMAIN);
    }

    @Test
    public void testStandalonePaths() throws Exception {
        testPaths(ServerType.STANDALONE);
    }

    private void testPaths(final ServerType serverType) throws Exception {
        final StringBuilder failureMessage = new StringBuilder();

        // Create the path names to test
        final Collection<String> pathNames = new ArrayList<>(defaultPathNames);
        if (!Environment.isWindows()) {
            pathNames.addAll(linuxPathNames);
        }
        // Check for addition paths
        final String additionalPaths = System.getProperty("wildfly.script.paths");
        if (additionalPaths != null && !additionalPaths.isEmpty()) {
            pathNames.addAll(Arrays.asList(additionalPaths.split(Pattern.quote(File.pathSeparator))));
        }

        final Path wildflyHome = Environment.WILDFLY_HOME;

        for (String pathName : pathNames) {
            Logger.getLogger(ScriptPathsTestCase.class).infof("Running %s", pathName);
            Path path = null;
            try {
                // Copy the path into a new directory
                path = Directories.copy(wildflyHome, wildflyHome.getParent().resolve(pathName).normalize());
                Process p = null;
                try (final ServerScriptRunner scriptRunner = ServerScriptRunner.of(path, serverType, serverType.type + "-" + pathName)) {
                    p = scriptRunner.startAndWait();
                    // If the process has died, the start failed
                    if (ProcessHelper.processHasDied(p)) {
                        final StringBuilder msg = new StringBuilder("Process has died: ").append(p.exitValue()).append(NEW_LINE);
                        for (String line : scriptRunner.readConsoleLines()) {
                            msg.append(line).append(NEW_LINE);
                        }
                        failureMessage.append(msg);
                    }
                    scriptRunner.shutdown();
                    // Wait for a bit before we continue to ensure everything shuts down
                    TimeUnit.SECONDS.sleep(2L);
                } finally {
                    ProcessHelper.destroyProcess(p);
                }
            } finally {
                if (path != null) {
                    try {
                        Directories.recursiveDelete(path);
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        // Wait for a moment to ensure things are cleaned up
        try {
            TimeUnit.SECONDS.sleep(2L);
        } catch (InterruptedException ignore) {

        }
        if (failureMessage.length() > 0) {
            Assert.fail(failureMessage.toString());
        }
    }
}

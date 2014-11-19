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
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.test.util.Directories;
import org.wildfly.test.util.Environment;
import org.wildfly.test.util.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ScriptPathsTestCase {

    private static final Logger LOGGER = Logger.getLogger(ScriptPathsTestCase.class);

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
            "wildfly'home",
            "wildfly- (home)"
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

    // "wildfly-home" is a known failure on Linux, but a valid path character and will not be tested at this point

    private final List<String> linuxPathNames = Arrays.asList(
            "wildfly?home",
            "wildfly\\home",
            "wildfly<home",
            "wildfly>home",
            // Known to fail on Windows due to encoding issues so only test on Linux
            "wildfly-\u017dlut\u00fdK\u016f\u0148",
            "wildfly|path"
    );

    @Test
    public void testDomainPaths() throws Exception {
        testPaths(ServerType.DOMAIN);
    }

    @Test
    public void testStandalonePaths() throws Exception {
        testPaths(ServerType.STANDALONE);
    }

    @Test
    public void testCliPaths() throws Exception {
        // Create the path names to test
        final Collection<String> pathNames = new ArrayList<>();

        // Check for the system property
        final String testPathsValue = System.getProperty("wildfly.test.paths");
        if (testPathsValue != null && !testPathsValue.isEmpty()) {
            pathNames.addAll(Arrays.asList(testPathsValue.split(Pattern.quote(File.pathSeparator))));
        } else {
            // Load the default paths
            pathNames.addAll(defaultPathNames);
            if (!Environment.isWindows()) {
                pathNames.addAll(linuxPathNames);
            }
        }

        boolean failed = false;
        final Path wildflyHome = Environment.WILDFLY_HOME;

        for (String pathName : pathNames) {
            LOGGER.infof("Running CLI %s", pathName);
            Path path = null;
            try {
                // Copy the path into a new directory
                path = Directories.copy(wildflyHome, wildflyHome.getParent().resolve(pathName).normalize());
                Process scriptProcess = null;
                Process serverProcess = null;
                try (final ScriptRunner scriptRunner = ScriptRunner.of(path, Scripts.scriptName("jboss-cli"))) {
                    // Start a standalone instance
                    final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(path);
                    serverProcess = Launcher.of(commandBuilder)
                            .setRedirectErrorStream(true)
                            .setDirectory(path.normalize())
                            .redirectOutput(scriptRunner.getTempDir().resolve("standalone-output-" + pathName + ".log"))
                            .addEnvironmentVariables(Environment.ENV)
                            .launch();
                    ;
                    ServerHelper.waitForStandalone(serverProcess);
                    scriptProcess = scriptRunner.start("-c", "--command=:shutdown");
                    // Wait for a bit to ensure the command had time to execute and the server time to shutdown
                    TimeUnit.SECONDS.sleep(2L);
                    final List<String> consoleLines = scriptRunner.readConsoleLines();
                    if (!consoleLines.contains("{\"outcome\" => \"success\"}")) {
                        failed = true;
                        final StringBuilder failureMessage = new StringBuilder().append("Failed to find a successful message for path '")
                                .append(path)
                                .append("' : ")
                                .append(NEW_LINE);
                        for (String line : consoleLines) {
                            failureMessage.append('\t').append(line).append(NEW_LINE);
                        }
                        LOGGER.error(failureMessage.toString());
                    }
                    // Ensure the server has been shutdown
                    if (ServerHelper.isStandaloneRunning()) {
                        ServerHelper.shutdownStandalone();
                        LOGGER.errorf("The server was not shut down via the cli :shutdown command for path '%s'", path);
                    }
                    if (!failed) {
                        LOGGER.infof("Success %s", pathName);
                    }
                } finally {
                    ProcessHelper.destroyProcess(scriptProcess);
                    ProcessHelper.destroyProcess(serverProcess);
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
        if (failed) {
            Assert.fail("One or more tests have failed. See the above logs to determine the failure.");
        }
    }

    private void testPaths(final ServerType serverType) throws Exception {
        // Create the path names to test
        final Collection<String> pathNames = new ArrayList<>();

        // Check for the system property
        final String testPathsValue = System.getProperty("wildfly.test.paths");
        if (testPathsValue != null && !testPathsValue.isEmpty()) {
            pathNames.addAll(Arrays.asList(testPathsValue.split(Pattern.quote(File.pathSeparator))));
        } else {
            // Load the default paths
            pathNames.addAll(defaultPathNames);
            if (!Environment.isWindows()) {
                pathNames.addAll(linuxPathNames);
            }
        }

        final StringBuilder failureMessage = new StringBuilder();
        final Path wildflyHome = Environment.WILDFLY_HOME;

        for (String pathName : pathNames) {
            LOGGER.infof("Running %s %s", serverType, pathName);
            Path path = null;
            try {
                // Copy the path into a new directory
                path = Directories.copy(wildflyHome, wildflyHome.getParent().resolve(pathName).normalize());
                Process p = null;
                try (final ServerScriptRunner scriptRunner = ServerScriptRunner.of(path, serverType, serverType.type + "-" + pathName)) {
                    p = scriptRunner.startAndWait();
                    // If the process has died, the start failed
                    if (ProcessHelper.processHasDied(p)) {
                        final StringBuilder msg = new StringBuilder("Process has died: ")
                                .append(p.exitValue())
                                .append(NEW_LINE)
                                .append("Attempted Path: ")
                                .append(pathName)
                                .append(NEW_LINE);
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

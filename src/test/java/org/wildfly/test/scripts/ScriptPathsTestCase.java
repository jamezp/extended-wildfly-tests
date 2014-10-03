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

import static org.wildfly.test.util.ServerHelper.shutdownDomain;
import static org.wildfly.test.util.ServerHelper.shutdownStandalone;
import static org.wildfly.test.util.ServerHelper.waitForDomain;
import static org.wildfly.test.util.ServerHelper.waitForStandalone;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.test.util.Directories;
import org.wildfly.test.util.Platform;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ScriptPathsTestCase extends AbstractScriptTest {

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

    private final Path wildflyHome = Paths.get(System.getProperty("wildfly.home"));
    private final String hostname = System.getProperty("wildfly.hostname", "localhost");
    private final int port = Integer.parseInt(System.getProperty("wildfly.port", "9990"));

    @Test
    public void testPaths() throws Exception {

        final String nl = String.format("%n");
        final StringBuilder failureMessage = new StringBuilder();

        // Create the path names to test
        final Collection<String> pathNames = new ArrayList<>(defaultPathNames);
        if (!Platform.isWindows()) {
            pathNames.addAll(linuxPathNames);
        }
        // Check for addition paths
        final String additionalPaths = System.getProperty("wildfly.script.paths");
        if (additionalPaths != null && !additionalPaths.isEmpty()) {
            pathNames.addAll(Arrays.asList(additionalPaths.split(Pattern.quote(File.pathSeparator))));
        }

        for (String pathName : pathNames) {
            Path path = null;
            try (final ModelControllerClient client = ModelControllerClient.Factory.create(hostname, port)) {
                // Copy the path into a new directory
                path = Directories.copy(wildflyHome, wildflyHome.getParent().resolve(pathName).normalize());

                // Test a domain launch
                try {
                    launchDomain(client, path);
                } catch (Exception e) {
                    failureMessage.append("Domain Failure: \"").append(pathName).append("\" : ").append(e.getMessage()).append(nl);
                }

                // Test a standalone launch
                try {
                    launchStandalone(client, path);
                } catch (Exception e) {
                    failureMessage.append("Standalone Failure: \"").append(pathName).append("\" : ").append(e.getMessage()).append(nl);
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


    static void launchDomain(final ModelControllerClient client, final Path path) throws IOException, InterruptedException {
        Process p = null;
        try {
            final DomainClient domainClient = DomainClient.Factory.create(client);
            p = start(path, DOMAIN_SCRIPT, Collections.<String>emptyList());
            final Map<ServerIdentity, ServerStatus> servers = waitForDomain(p, domainClient);
            // If the process has died, the start failed
            if (ProcessHelper.processHasDied(p)) {
                throw new RuntimeException(String.format("Process has died: %d", p.exitValue()));
            }
            shutdownDomain(domainClient, servers);
            // Wait for a bit before we continue to ensure everything shuts down
            TimeUnit.SECONDS.sleep(5L);
        } finally {
            ProcessHelper.destroyProcess(p);
        }
    }

    static void launchStandalone(final ModelControllerClient client, final Path path) throws IOException, InterruptedException {
        Process p = null;
        try {
            p = start(path, STANDALONE_SCRIPT, Collections.<String>emptyList());
            waitForStandalone(p, client);
            // If the process has died, the start failed
            if (ProcessHelper.processHasDied(p)) {
                throw new RuntimeException(String.format("Process has died: %d", p.exitValue()));
            }
            shutdownStandalone(client);
            // Wait for a bit before we continue to ensure everything shuts down
            TimeUnit.SECONDS.sleep(2L);
        } finally {
            ProcessHelper.destroyProcess(p);
        }
    }
}

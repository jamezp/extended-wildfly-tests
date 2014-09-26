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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.logging.Logger;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.test.Configuration;
import org.wildfly.test.TestRunner;
import org.wildfly.test.util.Directories;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ScriptTests implements TestRunner {

    private static final Logger LOGGER = Logger.getLogger(ScriptTests.class);

    private final List<String> pathNames = Arrays.asList("wildfly spaced", "wildfly double  spaced", "wildfly%home", "wild(fly)");

    @Override
    public void run(final Configuration config) {
        final Map<String, Exception> invalidDomainPaths = new LinkedHashMap<>();
        final Collection<String> validDomainPaths = new ArrayList<>();
        final Map<String, Exception> invalidStandalonePaths = new LinkedHashMap<>();
        final Collection<String> validStandalonePaths = new ArrayList<>();
        for (String pathName : pathNames) {
            Path path = null;
            try {
                final Path defaultPath = config.getWildFlyHome();
                // Copy the path inot a new directory
                path = Directories.copy(defaultPath, defaultPath.getParent().resolve(pathName).normalize());

                // Test a domain launch
                try {
                    launchDomain(config, path);
                    validDomainPaths.add(pathName);
                } catch (Exception e) {
                    invalidDomainPaths.put(pathName, e);
                }

                // Test a standalone launch
                try {
                    launchStandalone(config, path);
                    validStandalonePaths.add(pathName);
                } catch (Exception e) {
                    invalidStandalonePaths.put(pathName, e);
                }
            } catch (IOException e) {
                LOGGER.errorf(e, "Could not process path name '%s'", pathName);
            } finally {
                if (path != null) {
                    try {
                        Directories.recursiveDelete(path);
                    } catch (IOException e) {
                        LOGGER.errorf(e, "Could not delete directory '%s'", path);
                    }
                }
            }
        }
        // Wait for a moment to ensure things are cleaned up
        try {
            TimeUnit.SECONDS.sleep(2L);
        } catch (InterruptedException ignore) {

        }
        print("Domain", config, validDomainPaths, invalidDomainPaths);
        print("Standalone", config, validStandalonePaths, invalidStandalonePaths);
    }

    private void launchDomain(final Configuration config, final Path path) throws IOException, InterruptedException {
        final String scriptName = scriptName("domain");
        Process p = null;
        try {
            final DomainClient client = DomainClient.Factory.create(config.getClient());
            p = start(path, scriptName);
            final Map<ServerIdentity, ServerStatus> servers = waitForDomain(p, client);
            shutdownDomain(client, servers);
            // Wait for a bit before we continue to ensure everything shuts down
            TimeUnit.SECONDS.sleep(5L);
        } finally {
            ProcessHelper.destroyProcess(p);
        }
    }

    private void launchStandalone(final Configuration config, final Path path) throws IOException, InterruptedException {
        final String scriptName = scriptName("standalone");
        Process p = null;
        try {
            p = start(path, scriptName);
            waitForStandalone(p, config.getClient());
            shutdownStandalone(config.getClient());
            // Wait for a bit before we continue to ensure everything shuts down
            TimeUnit.SECONDS.sleep(2L);
        } finally {
            ProcessHelper.destroyProcess(p);
        }
    }

    private Process start(final Path home, final String scriptName) throws IOException, InterruptedException {
        final Path scriptPath = home.resolve("bin").resolve(scriptName).normalize();
        final ProcessBuilder processBuilder = new ProcessBuilder(Arrays.asList(scriptPath.toString()))
                .directory(home.toFile())
                .inheritIO();
        final Process p = processBuilder.start();
        ProcessHelper.addShutdownHook(p);
        if (ProcessHelper.processHasDied(p)) {
            // We have an error
            throw new RuntimeException(String.format("Failed to start with path of '%s'", home));
        }
        return p;
    }

    private void print(final String name, final Configuration config, final Collection<String> validPaths, final Map<String, Exception> invalidPaths) {
        final String sep = fill('*', 80);
        final PrintStream out = config.out();
        out.println();
        out.println(sep);
        out.printf("** Valid %s Paths: %s%n", name, config.getWildFlyHome().getParent());
        out.println(sep);
        for (String valid : validPaths) {
            out.printf("\t%s%n", valid);
        }
        out.println();
        out.println(sep);
        out.printf("** %s paths with start errors: %s%n", name, config.getWildFlyHome().getParent());
        out.println(sep);
        for (Map.Entry<String, Exception> entry : invalidPaths.entrySet()) {
            out.printf("\t%s: %s%n", entry.getKey(), entry.getValue());
        }
    }

    private static String scriptName(final String prefix) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            return prefix + ".bat";
        }
        return prefix + ".sh";
    }

    private static String fill(final char c, final int len) {
        final StringBuilder result = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            result.append(c);
        }
        return result.toString();
    }
}

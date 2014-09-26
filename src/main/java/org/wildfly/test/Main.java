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

package org.wildfly.test;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ServiceLoader;

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.test.util.Directories;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Main {

    private static final PrintStream out = System.out;
    private static final PrintStream err = System.err;

    public static void main(final String[] args) throws Exception {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        if (args == null || args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String host = "127.0.0.1";
        int port = 9990;
        String path = null;
        final Collection<String> arguments = new ArrayList<>();

        // Process the arguments
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            switch (arg) {
                case "--host":
                    host = args[++i];
                    break;
                case "--port":
                    try {
                        port = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        err.printf("Invalid port: %s%n", e.getMessage());
                        printUsage();
                        System.exit(0);
                    }
                    break;
                case "-h":
                    printUsage();
                    System.exit(0);
                default:
                    if (path == null) {
                        path = arg;
                    } else {
                        arguments.add(arg);
                    }
                    break;
            }
        }
        if (path == null) {
            printUsage();
            System.exit(1);
        }
        final Path wildfly = Paths.get(args[0]).normalize();
        // Validate the home directory
        if (Files.notExists(wildfly)) {
            printUsage();
            System.exit(1);
        }
        // Temp directory
        final Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "wildfly-tests");
        // Delete the file if it exists
        if (Files.exists(tmpDir)) {
            Directories.recursiveDelete(tmpDir);
        }
        Files.createDirectories(tmpDir);
        // Copy WildFly to a temporary directory
        final Path wildflyTmp = Directories.copy(wildfly, tmpDir.resolve("default-wildfly"));
        try (final ModelControllerClient client = ModelControllerClient.Factory.create(host, port)) {
            final Configuration config = new Configuration() {
                @Override
                public Path getWildFlyHome() {
                    return wildflyTmp;
                }

                @Override
                public ModelControllerClient getClient() {
                    return client;
                }

                @Override
                public PrintStream out() {
                    return out;
                }

                @Override
                public PrintStream err() {
                    return err;
                }

                @Override
                public Collection<String> getArguments() {
                    return Collections.unmodifiableCollection(arguments);
                }
            };
            final ServiceLoader<TestRunner> serviceLoader = ServiceLoader.load(TestRunner.class);
            for (final TestRunner aServiceLoader : serviceLoader) {
                aServiceLoader.run(config);
            }
        } finally {
            try {
                Directories.recursiveDelete(tmpDir);
            } catch (Exception ignore) {
            }
        }
    }

    private static void printUsage() {
        final String nl = String.format("%n");
        StringBuilder usage = new StringBuilder();
        usage.append("Usage:").append(nl);
        usage.append('\t').append("--host <hostname> (default is 127.0.0.1)").append(nl);
        usage.append('\t').append("--port <port> (default is 9990)").append(nl);
        usage.append('\t').append("<wildfly-path>").append(nl);
        usage.append('\t').append("Example: java -jar extended-wildfly-tests.jar -h 127.0.0.1 -p 9990 /opt/wildfly-core/").append(nl);
        err.println(usage);
    }
}

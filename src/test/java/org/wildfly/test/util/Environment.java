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

package org.wildfly.test.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Environment {

    public static final String NEW_LINE = String.format("%n");
    public static final Path WILDFLY_HOME;
    public static final String HOSTNAME = System.getProperty("wildfly.hostname", "localhost");
    public static final int PORT;
    public static final Path TMP_DIR;

    static {
        // Get the temp directory
        String tmpDir = System.getProperty("tmp.dir");
        if (tmpDir == null) {
            TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "wildfly-test-runner");
        } else {
            TMP_DIR = Paths.get(tmpDir);
        }
        if (Files.notExists(TMP_DIR)) {
            try {
                Files.createDirectories(TMP_DIR);
            } catch (IOException e) {
                throw new RuntimeException("Could not create the temp directory: " + TMP_DIR, e);
            }
        }

        // Get the WildFly home directory and copy to the temp directory
        final String wildflyDist = System.getProperty("wildfly.dist");
        if (wildflyDist == null) {
            throw new RuntimeException("WildFly home property was not set");
        }
        Path wildflyHome = Paths.get(wildflyDist);
        validateWildFlyHome(wildflyHome);
        // Copy the dist into the temp directory
        WILDFLY_HOME = TMP_DIR.resolve("wildfly");
        try {
            Directories.copy(wildflyHome, WILDFLY_HOME);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy WildFly Dist", e);
        }

        String port = System.getProperty("wildfly.port", "9990");
        try {
            PORT = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid port: " + port, e);
        }
    }

    public static boolean isValidWildFlyHome(final Path wildflyHome) {
        return Files.exists(wildflyHome) && Files.isDirectory(wildflyHome) && Files.exists(wildflyHome.resolve("jboss-modules.jar"));
    }

    public static void validateWildFlyHome(final Path wildflyHome) {
        if (!isValidWildFlyHome(wildflyHome)) {
            throw new RuntimeException("Invalid WildFly home directory: " + wildflyHome);
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }
}

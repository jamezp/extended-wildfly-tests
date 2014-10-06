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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Environment {

    /**
     * The default new line string for the environment
     */
    public static final String NEW_LINE = String.format("%n");
    /**
     * The default WildFly home directory specified by the {@code wildfly.dist} system property.
     * <p/>
     * Note that the {@code wildfly.dist} will not match the path specified here. The WildFly distribution is copied to
     * a temporary directory to keep the environment clean.
     * }
     */
    public static final Path WILDFLY_HOME;
    /**
     * The host name specified by the {@code wildfly.hostname} system property or {@code localhost} by default.
     */
    public static final String HOSTNAME = System.getProperty("wildfly.hostname", "localhost");
    /**
     * The port specified by the {@code wildfly.port} system property or {@code 9990} by default.
     */
    public static final int PORT;
    /**
     * The temporary directory to use specified by the {@code tmp.dir} system property. If the property is not set,
     * {@code java.io.tmpdir} is used.
     */
    public static final Path TMP_DIR;
    /**
     * Additional environment variables.
     */
    public static final Map<String, String> ENV;

    static {
        final Logger logger = Logger.getLogger(Environment.class);
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
                logger.debugf(e, "Could not create the temp directory: %s", TMP_DIR);
                throw new RuntimeException("Could not create the temp directory: " + TMP_DIR, e);
            }
        }

        // Get the WildFly home directory and copy to the temp directory
        final String wildflyDist = System.getProperty("wildfly.dist");
        assert wildflyDist != null : "WildFly home property, wildfly.dist, was not set";
        Path wildflyHome = Paths.get(wildflyDist);
        validateWildFlyHome(wildflyHome);
        // Copy the dist into the temp directory
        WILDFLY_HOME = TMP_DIR.resolve("wildfly");
        if (Files.exists(WILDFLY_HOME)) {
            try {
                Directories.recursiveDelete(WILDFLY_HOME);
            } catch (IOException e) {
                logger.debugf(e, "Could not create the temp directory: %s", TMP_DIR);
                throw new RuntimeException("Could not create the temp directory: " + WILDFLY_HOME, e);
            }
        }
        try {
            Files.createDirectories(WILDFLY_HOME);
        } catch (IOException e) {
            logger.debugf(e, "Could not create the temp directory: %s", TMP_DIR);
            throw new RuntimeException("Could not create the temp directory: " + WILDFLY_HOME, e);
        }
        try {
            Directories.copy(wildflyHome, WILDFLY_HOME);
        } catch (IOException e) {
            logger.debug("Failed to copy WildFly Dist", e);
            throw new RuntimeException("Failed to copy WildFly Dist", e);
        }

        String port = System.getProperty("wildfly.port", "9990");
        try {
            PORT = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            logger.debugf(e, "Invalid port: %d", port);
            throw new RuntimeException("Invalid port: " + port, e);
        }
        // Create any custom environment variables
        final Map<String, String> env = new HashMap<>();
        if (isWindows()) {
            env.put("NOPAUSE", "true");
        }
        ENV = Collections.unmodifiableMap(env);
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

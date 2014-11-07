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

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.test.util.Environment;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ScriptRunner implements Closeable {
    static final Logger LOGGER = Logger.getLogger(ScriptRunner.class.getPackage().getName());

    private final Path wildflyHome;
    private final String scriptName;
    private Path consolePath;
    private Process currentProcess;

    protected ScriptRunner(final Path wildflyHome, final String scriptName) {
        this.wildflyHome = wildflyHome;
        this.scriptName = scriptName;
    }

    public static ScriptRunner of(final Path wildflyHome, final String scriptName) throws IOException {
        Environment.validateWildFlyHome(wildflyHome);
        return new ScriptRunner(wildflyHome, scriptName);
    }

    public final Process start(final String... args) throws IOException {
        return start(Arrays.asList(args));
    }

    public final Process start(final Collection<String> args) throws IOException {
        if (currentProcess != null) {
            throw new IllegalStateException("Script already started and close() was not invoked.");
        }
        consolePath = createConsolePath();
        final Path scriptPath = wildflyHome.resolve("bin").resolve(scriptName).normalize();
        final List<String> cmd = new ArrayList<>();
        cmd.add(scriptPath.toString());
        cmd.addAll(args);
        LOGGER.debugf("Starting with command: %s", cmd);
        final ProcessBuilder processBuilder = new ProcessBuilder(cmd)
                .directory(wildflyHome.toFile())
                .redirectErrorStream(true)
                .redirectOutput(consolePath.toFile());
        processBuilder.environment().putAll(Environment.ENV);
        final Process p = processBuilder.start();
        ProcessHelper.addShutdownHook(p);
        currentProcess = p;
        return p;
    }

    public List<String> readConsoleLines() throws IOException {
        if (currentProcess == null || (consolePath != null && Files.notExists(consolePath))) {
            return Collections.emptyList();
        }
        // Underlying redirect uses FileOutputStream which uses the default encoding
        return Files.readAllLines(consolePath, Charset.defaultCharset());
    }

    @Override
    public void close() throws IOException {
        final Process p = currentProcess;
        currentProcess = null;
        try {
            ProcessHelper.destroyProcess(p);
        } catch (InterruptedException ignore) {
        }
    }

    protected Path createConsolePath() throws IOException {
        return Files.createTempFile(getTempDir(), stripSuffix(scriptName), ".log");
    }

    protected Path getTempDir() throws IOException {
        final Path tempDir = Environment.TMP_DIR.resolve("console-output");
        if (Files.notExists(tempDir)) {
            Files.createDirectories(tempDir);
        }
        return tempDir;
    }

    private static String stripSuffix(final String scriptName) {
        final int dotIndex = scriptName.lastIndexOf('.');
        if (dotIndex > 0) {
            return scriptName.substring(0, dotIndex);
        }
        return scriptName;
    }
}

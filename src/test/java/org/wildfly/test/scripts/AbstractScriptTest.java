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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.test.util.Platform;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractScriptTest {
    static final String DOMAIN_SCRIPT = scriptName("domain");
    static final String STANDALONE_SCRIPT = scriptName("standalone");

    static Process start(final Path home, final String scriptName, final Collection<String> args) throws IOException, InterruptedException {
        final Path scriptPath = home.resolve("bin").resolve(scriptName).normalize();
        final List<String> cmd = new ArrayList<>();
        cmd.add(scriptPath.toString());
        cmd.addAll(args);
        final ProcessBuilder processBuilder = new ProcessBuilder(cmd)
                .directory(home.toFile())
                .inheritIO();
        final Process p = processBuilder.start();
        ProcessHelper.addShutdownHook(p);
        return p;
    }

    static String scriptName(final String prefix) {
        if (Platform.isWindows()) {
            return prefix + ".bat";
        }
        return prefix + ".sh";
    }
}

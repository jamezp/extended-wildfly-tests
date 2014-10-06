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

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.launcher.ProcessHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ScriptArgumentsTestCase {

    private static final ExecutorService SERVICE = Executors.newCachedThreadPool();

    private static final Pattern INVALID_OPTION_PATTERN = Pattern.compile("'.+'.+--help");

    @AfterClass
    public static void tearDown() {
        SERVICE.shutdown();
    }

    @Test
    public void testHelpArgument() throws Exception {
        testHelp(ServerType.DOMAIN);
        testHelp(ServerType.STANDALONE);
    }

    private void testHelp(ServerType serverType) throws Exception {
        try (final ServerScriptRunner runner = ServerScriptRunner.of(WILDFLY_HOME, serverType)) {

            Process process = runner.start("--help");
            // Wait until the process is dead, then read the output
            if (isAlive(process, 2L)) {
                // The process did not end quickly, could be running
                try {
                    runner.shutdown();
                } catch (IOException ignore) {

                }
                StringBuilder msg = new StringBuilder()
                        .append(serverType.name)
                        .append(" --help failed:")
                        .append(NEW_LINE);
                for (String line : runner.readConsoleLines()) {
                    msg.append(line).append(NEW_LINE);
                }
                Assert.fail(msg.toString());
            }
            boolean found = false;
            boolean invalidOptionFound = false;
            Pattern pattern = Pattern.compile("(.*:\\s+" + serverType.type + "\\.(sh|bat)\\s+\\[.+)");
            StringBuilder msg = new StringBuilder()
                    .append(serverType.name)
                    .append(" --help failed:")
                    .append(NEW_LINE);
            for (String line : runner.readConsoleLines()) {
                if (pattern.matcher(line).matches()) {
                    found = true;
                }
                if (INVALID_OPTION_PATTERN.matcher(line).find()) {
                    invalidOptionFound = true;
                }
                msg.append(line).append(NEW_LINE);
            }
            Assert.assertTrue(msg.toString(), found);
            Assert.assertFalse(msg.toString(), invalidOptionFound);
        }
    }


    static boolean isAlive(final Process process, final long waitTime) {
        final Future<Boolean> future = SERVICE.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                while (!ProcessHelper.processHasDied(process)) {
                    TimeUnit.MILLISECONDS.sleep(200L);
                }
                return Boolean.FALSE;
            }
        });
        try {
            return future.get(waitTime, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            try {
                ProcessHelper.destroyProcess(process);
            } catch (InterruptedException ignore) {
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}

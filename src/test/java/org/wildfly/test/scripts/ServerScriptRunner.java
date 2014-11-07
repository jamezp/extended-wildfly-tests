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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.wildfly.test.util.Environment;
import org.wildfly.test.util.ServerHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class ServerScriptRunner extends ScriptRunner implements Closeable {
    private ServerScriptRunner(final Path wildflyHome, final String scriptName) {
        super(wildflyHome, scriptName);
    }

    static ServerScriptRunner of(final Path wildflyHome, final ServerType serverType) throws IOException {
        return (serverType == ServerType.DOMAIN ? createDomain(wildflyHome, null) : createStandalone(wildflyHome, null));
    }

    static ServerScriptRunner of(final Path wildflyHome, final ServerType serverType, final String consoleOutputName) throws IOException {
        return (serverType == ServerType.DOMAIN ? createDomain(wildflyHome, consoleOutputName) : createStandalone(wildflyHome, consoleOutputName));
    }

    static ServerScriptRunner createStandalone(final Path wildflyHome, final String consoleOutputName) throws IOException {
        Environment.validateWildFlyHome(wildflyHome);
        final ModelControllerClient client = ServerHelper.createClientConnection();
        return new ServerScriptRunner(wildflyHome, Scripts.STANDALONE_SCRIPT) {
            @Override
            void shutdown() throws IOException {
                ServerHelper.shutdownStandalone(client);
                this.isShutdown = true;
            }

            @Override
            Process startAndWait(final Collection<String> args) throws IOException, InterruptedException {
                final Process process = start(args);
                ServerHelper.waitForStandalone(process, client);
                return process;
            }

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    client.close();
                }
            }

            @Override
            protected Path createConsolePath() throws IOException {
                if (consoleOutputName == null) {
                    return super.createConsolePath();
                }
                return getTempDir().resolve(consoleOutputName + ".log");
            }
        };
    }

    static ServerScriptRunner createDomain(final Path wildflyHome, final String consoleOutputName) throws IOException {
        Environment.validateWildFlyHome(wildflyHome);
        final DomainClient client = DomainClient.Factory.create(ServerHelper.createClientConnection());
        final Map<ServerIdentity, ServerStatus> servers = new ConcurrentHashMap<>();
        return new ServerScriptRunner(wildflyHome, Scripts.DOMAIN_SCRIPT) {
            @Override
            void shutdown() throws IOException {
                ServerHelper.shutdownDomain(client, servers);
                this.isShutdown = true;
            }

            @Override
            Process startAndWait(final Collection<String> args) throws IOException, InterruptedException {
                final Process process = start(args);
                ServerHelper.waitForDomain(process, client);
                return process;
            }

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    client.close();
                }
            }

            @Override
            protected Path createConsolePath() throws IOException {
                if (consoleOutputName == null) {
                    return super.createConsolePath();
                }
                return getTempDir().resolve(consoleOutputName + ".log");
            }
        };
    }

    protected volatile boolean isShutdown = false;

    Process startAndWait() throws IOException, InterruptedException {
        return startAndWait(Collections.<String>emptyList());
    }

    Process startAndWait(final String... args) throws IOException, InterruptedException {
        return startAndWait(Arrays.asList(args));
    }

    abstract Process startAndWait(final Collection<String> args) throws IOException, InterruptedException;

    abstract void shutdown() throws IOException;

    @Override
    public void close() throws IOException {
        try {
            if (!isShutdown) {
                shutdown();
            }
        } finally {
            super.close();
        }
    }
}

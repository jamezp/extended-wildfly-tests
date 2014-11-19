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

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.core.launcher.ProcessHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerHelper {
    public static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();
    private static final Logger LOGGER = Logger.getLogger(ServerHelper.class);

    static {
        EMPTY_ADDRESS.protect();
    }

    public static ModelControllerClient createClientConnection() throws UnknownHostException {
        return ModelControllerClient.Factory.create(Environment.HOSTNAME, Environment.PORT);
    }


    public static Map<ServerIdentity, ServerStatus> waitForDomain(final Process process, final DomainClient client) throws InterruptedException, IOException {
        final Map<ServerIdentity, ServerStatus> servers = new HashMap<>();
        long startupTimeout = 30;
        long timeout = startupTimeout * 1000;
        boolean serverAvailable = false;
        long sleep = 1000;
        while (timeout > 0 && !serverAvailable) {
            long before = System.currentTimeMillis();
            serverAvailable = isDomainRunning(client, servers);
            timeout -= (System.currentTimeMillis() - before);
            if (!serverAvailable) {
                if (ProcessHelper.processHasDied(process)) {
                    return Collections.emptyMap();
                }
                Thread.sleep(sleep);
                timeout -= sleep;
                sleep = Math.max(sleep / 2, 100);
            }
        }
        if (!serverAvailable) {
            throw new RuntimeException("Server was not started within 30s");
        }
        return servers;
    }

    public static boolean isDomainRunning(final DomainClient client, final Map<ServerIdentity, ServerStatus> servers) {
        return isDomainRunning(client, servers, false);
    }

    public static void shutdownDomain(final DomainClient client, final Map<ServerIdentity, ServerStatus> servers) {
        final ModelNode address = new ModelNode().setEmptyList().add("host", "master");
        try {
            // First shutdown the servers
            ModelNode op = Operations.createOperation("stop-servers");
            ModelNode response = client.execute(op);
            // Wait for a moment so they servers can shut down
            TimeUnit.SECONDS.sleep(3L);
            if (Operations.isSuccessfulOutcome(response)) {
                op = Operations.createOperation("shutdown", address);
                response = client.execute(op);
                if (Operations.isSuccessfulOutcome(response)) {
                    // Wait until the process has died
                    while (true) {
                        if (isDomainRunning(client, servers, true)) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(20L);
                            } catch (InterruptedException e) {
                                LOGGER.trace("Interrupted during sleep", e);
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
                }
            } else {
                LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.trace("Error shutting down domain", e);
        }
    }

    public static void waitForStandalone(final Process process) throws InterruptedException, IOException {
        try (final ModelControllerClient client = createClientConnection()) {
            waitForStandalone(process, client);
        }
    }

    public static void waitForStandalone(final Process process, final ModelControllerClient client) throws InterruptedException, IOException {
        long startupTimeout = 30;
        long timeout = startupTimeout * 1000;
        boolean serverAvailable = false;
        long sleep = 1000;
        while (timeout > 0 && !serverAvailable) {
            long before = System.currentTimeMillis();
            serverAvailable = isStandaloneRunning(client);
            timeout -= (System.currentTimeMillis() - before);
            if (!serverAvailable) {
                if (ProcessHelper.processHasDied(process)) {
                    return;
                }
                Thread.sleep(sleep);
                timeout -= sleep;
                sleep = Math.max(sleep / 2, 100);
            }
        }
        if (!serverAvailable) {
            throw new RuntimeException("Server was not started within 30s");
        }
    }

    public static boolean isStandaloneRunning() {
        try (final ModelControllerClient client = createClientConnection()) {
            return isStandaloneRunning(client);
        } catch (RuntimeException | IOException e) {
            LOGGER.trace("Interrupted determining if standalone is running", e);
        }
        return false;
    }

    public static boolean isStandaloneRunning(final ModelControllerClient client) {
        try {
            final ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state"));
            if (Operations.isSuccessfulOutcome(response)) {
                final String state = Operations.readResult(response).asString();
                return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                        && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
            }
        } catch (RuntimeException | IOException e) {
            LOGGER.trace("Interrupted determining if standalone is running", e);
        }
        return false;
    }

    public static void shutdownStandalone() {
        try (final ModelControllerClient client = createClientConnection()) {
            shutdownStandalone(client);
        } catch (IOException e) {
            LOGGER.trace("Interrupted shutting down standalone", e);
        }
    }

    public static void shutdownStandalone(final ModelControllerClient client) {
        try {
            final ModelNode op = Operations.createOperation("shutdown");
            final ModelNode response = client.execute(op);
            if (Operations.isSuccessfulOutcome(response)) {
                while (true) {
                    if (isStandaloneRunning(client)) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(20L);
                        } catch (InterruptedException e) {
                            LOGGER.trace("Interrupted during sleep", e);
                        }
                    } else {
                        break;
                    }
                }
            } else {
                LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
            }
        } catch (IOException e) {
            LOGGER.trace("Interrupted shutting down standalone", e);
        }
    }

    private static boolean isDomainRunning(final DomainClient client, final Map<ServerIdentity, ServerStatus> servers, boolean shutdown) {
        try {
            final Map<ServerIdentity, ServerStatus> statuses = client.getServerStatuses();
            for (ServerIdentity id : statuses.keySet()) {
                final ServerStatus status = statuses.get(id);
                switch (status) {
                    case DISABLED:
                    case STARTED: {
                        servers.put(id, status);
                        break;
                    }
                }
            }
            if (shutdown) {
                return statuses.isEmpty();
            }
            return statuses.size() == servers.size();
        } catch (Exception e) {
            LOGGER.trace("Interrupted determining if domain is running", e);
        }
        return false;
    }
}

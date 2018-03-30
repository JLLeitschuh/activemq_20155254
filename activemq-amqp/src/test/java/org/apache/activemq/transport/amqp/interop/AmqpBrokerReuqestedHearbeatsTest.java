/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.amqp.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpClientTestSupport;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpConnectionListener;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.activemq.util.Wait;
import org.apache.qpid.proton.engine.Connection;
import org.junit.Test;

/**
 * Test handling of heartbeats requested by the broker.
 */
public class AmqpBrokerReuqestedHearbeatsTest extends AmqpClientTestSupport {

    private final int TEST_IDLE_TIMEOUT = 3000;

    @Override
    protected String getAdditionalConfig() {
        return "&transport.wireFormat.idleTimeout=" + TEST_IDLE_TIMEOUT;
    }

    @Test(timeout = 60000)
    public void testBrokerSendsHalfConfiguredIdleTimeout() throws Exception {
        AmqpClient client = createAmqpClient();
        assertNotNull(client);

        client.setValidator(new AmqpValidator() {

            @Override
            public void inspectOpenedResource(Connection connection) {
                assertEquals(TEST_IDLE_TIMEOUT / 2, connection.getTransport().getRemoteIdleTimeout());
            }
        });

        AmqpConnection connection = client.connect();
        assertNotNull(connection);

        connection.getStateInspector().assertValid();
        connection.close();
    }

    @Test(timeout = 60000)
    public void testClientWithoutHeartbeatsGetsDropped() throws Exception {

        final CountDownLatch disconnected = new CountDownLatch(1);

        AmqpClient client = createAmqpClient();
        assertNotNull(client);

        AmqpConnection connection = client.createConnection();
        assertNotNull(connection);

        connection.setIdleProcessingDisabled(true);
        connection.setListener(new AmqpConnectionListener() {

            @Override
            public void onException(Throwable ex) {
                disconnected.countDown();
            }
        });

        connection.connect();

        assertEquals(1, getProxyToBroker().getCurrentConnectionsCount());
        assertTrue(disconnected.await(30, TimeUnit.SECONDS));

        connection.close();

        assertTrue("Connection should get cleaned up.", Wait.waitFor(new Wait.Condition() {

            @Override
            public boolean isSatisified() throws Exception {
                return getProxyToBroker().getCurrentConnectionsCount() == 0;
            }
        }));
    }

    @Test(timeout = 60000)
    public void testClientWithHeartbeatsStaysAlive() throws Exception {

        final CountDownLatch disconnected = new CountDownLatch(1);

        AmqpClient client = createAmqpClient();
        assertNotNull(client);

        AmqpConnection connection = client.createConnection();
        assertNotNull(connection);

        connection.setListener(new AmqpConnectionListener() {

            @Override
            public void onException(Throwable ex) {
                disconnected.countDown();
            }
        });

        connection.connect();

        assertEquals(1, getProxyToBroker().getCurrentConnectionsCount());
        assertFalse(disconnected.await(10, TimeUnit.SECONDS));

        connection.close();

        assertTrue("Connection should get cleaned up.", Wait.waitFor(new Wait.Condition() {

            @Override
            public boolean isSatisified() throws Exception {
                return getProxyToBroker().getCurrentConnectionsCount() == 0;
            }
        }));
    }
}

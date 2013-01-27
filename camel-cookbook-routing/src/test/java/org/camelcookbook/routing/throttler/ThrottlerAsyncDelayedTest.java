/*
 * Copyright (C) Scott Cranton and Jakub Korab
 * https://github.com/CamelCookbook
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camelcookbook.routing.throttler;

import org.apache.camel.CamelContext;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ThrottlerAsyncDelayedTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ThrottlerAsyncDelayedTest.class);

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new ThrottlerAsyncDelayedRouteBuilder();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camel = super.createCamelContext();

        ThreadPoolProfile profile = new ThreadPoolProfile("custom");
        profile.setPoolSize(10);
        profile.setMaxPoolSize(15);
        profile.setKeepAliveTime(25L);
        profile.setMaxQueueSize(250);
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);

        camel.getExecutorServiceManager().setDefaultThreadPoolProfile(profile);
        return camel;
    }

    @Test
    public void testAsyncDelayedThrottle() throws Exception {
        final int throttleRate = 5;
        final int messageCount = throttleRate + 2;

        getMockEndpoint("mock:unthrottled").expectedMessageCount(messageCount);
        getMockEndpoint("mock:throttled").expectedMessageCount(throttleRate);
        getMockEndpoint("mock:after").expectedMessageCount(throttleRate);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);

        // Send the message on separate threads as sendBody will block on the throttler
        final AtomicInteger threadCount = new AtomicInteger(0);
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    template.sendBody("direct:asyncDelayed", "Camel Rocks");

                    final int threadId = threadCount.incrementAndGet();
                    LOG.info("Thread {} finished", threadId);
                }
            });
        }

        assertMockEndpointsSatisfied();

        LOG.info("Threads completed {} of {}", threadCount.get(), messageCount);
        assertEquals("Threads completed should equal throttle rate", throttleRate, threadCount.get());

        executor.shutdownNow();
    }
}
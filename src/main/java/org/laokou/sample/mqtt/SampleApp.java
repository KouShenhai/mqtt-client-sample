/*
 * Copyright (c) 2022-2024 KCloud-Platform-IoT Author or Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.laokou.sample.mqtt;

import lombok.RequiredArgsConstructor;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.laokou.sample.mqtt.handler.MessageHandler;
import org.laokou.sample.mqtt.hivemq.HivemqClientV3;
import org.laokou.sample.mqtt.hivemq.HivemqClientV5;
import org.laokou.sample.mqtt.hivemq.HivemqProperties;
import org.laokou.sample.mqtt.paho.PahoMqttClientV3;
import org.laokou.sample.mqtt.paho.PahoMqttClientV5;
import org.laokou.sample.mqtt.paho.PahoProperties;
import org.laokou.sample.mqtt.util.ThreadUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author laokou
 */
@RequiredArgsConstructor
@SpringBootApplication(scanBasePackages = "org.laokou")
public class SampleApp implements CommandLineRunner {

    private final List<MessageHandler> messageHandlers;

    public static void main(String[] args) {
        SpringApplication.run(SampleApp.class, args);
    }

    @Override
    public void run(String... args) throws MqttException, InterruptedException, org.eclipse.paho.client.mqttv3.MqttException {
        HivemqProperties hivemqProperties = new HivemqProperties();
        hivemqProperties.setClientId("test-client-1");
        hivemqProperties.setTopics(Set.of("/test-topic-1/#"));
        HivemqClientV5 hivemqClientV5 = new HivemqClientV5(hivemqProperties, messageHandlers);
        hivemqClientV5.open();
        hivemqClientV5.publish("/test-topic-1/123", "Hello World123".getBytes());

        HivemqProperties hivemqProperties2 = new HivemqProperties();
        hivemqProperties2.setClientId("test-client-2");
        hivemqProperties2.setTopics(Set.of("/test-topic-2/#"));
        HivemqClientV3 hivemqClientV3 = new HivemqClientV3(hivemqProperties2, messageHandlers);
        hivemqClientV3.open();
        hivemqClientV3.publish("/test-topic-2/456", "Hello World456".getBytes());

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(16);

        PahoProperties pahoProperties = new PahoProperties();
        pahoProperties.setClientId("test-client-3");
        pahoProperties.setTopics(Set.of("/test-topic-3/#"));
        PahoMqttClientV5 pahoMqttClientV5 = new PahoMqttClientV5(pahoProperties, messageHandlers, scheduledExecutorService);
        pahoMqttClientV5.open();
        Thread.sleep(1000);
        pahoMqttClientV5.publish("/test-topic-3/789", "Hello World789".getBytes());

        PahoProperties pahoProperties2 = new PahoProperties();
        pahoProperties2.setClientId("test-client-4");
        pahoProperties2.setTopics(Set.of("/test-topic-4/#"));
        PahoMqttClientV3 pahoMqttClientV3 = new PahoMqttClientV3(pahoProperties2, messageHandlers, scheduledExecutorService);
        pahoMqttClientV3.open();
        Thread.sleep(1000);
        pahoMqttClientV3.publish("/test-topic-4/000", "Hello World000".getBytes());

    }
}

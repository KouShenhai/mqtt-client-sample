/*
 * Copyright (c) 2022-2025 KCloud-Platform-IoT Author or Authors. All Rights Reserved.
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

package org.laokou.sample.mqtt.paho;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.laokou.sample.mqtt.handler.MessageHandler;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * MQTT客户端.
 *
 * @author laokou
 */
@Slf4j
public class PahoMqttClientV3 {

	/**
	 * 服务下线数据.
	 */
	private final byte[] WILL_PAYLOAD = "offline".getBytes(UTF_8);

	private final PahoProperties pahoProperties;

	private final List<MessageHandler> messageHandlers;

	private final ScheduledExecutorService executor;

	private volatile MqttAsyncClient client;

	private final Object LOCK = new Object();

	public PahoMqttClientV3(PahoProperties pahoProperties, List<MessageHandler> messageHandlers,
                            ScheduledExecutorService executor) {
		this.pahoProperties = pahoProperties;
		this.messageHandlers = messageHandlers;
		this.executor = executor;
	}

	public void open() {
		try {
			if (Objects.isNull(client)) {
				synchronized (LOCK) {
					if (Objects.isNull(client)) {
						client = new MqttAsyncClient(
								"tcp://" + pahoProperties.getHost() + ":" + pahoProperties.getPort(),
								pahoProperties.getClientId(), new MqttDefaultFilePersistence(), new TimerPingSender(), executor);
					}
				}
			}
			connect();
		}
		catch (Exception e) {
			log.error("【Paho-V3】 => MQTT连接失败，错误信息：{}", e.getMessage(), e);
		}
	}

	public void close() {
		if (!Objects.isNull(client)) {
			// 等待30秒
			try {
				client.disconnectForcibly(30);
				client.close();
				log.info("【Paho-V3】 => 关闭MQTT连接成功");
			}
			catch (MqttException e) {
				log.error("【Paho-V3】 => 关闭MQTT连接失败，错误信息：{}", e.getMessage(), e);
			}
		}
	}

	public void subscribe(String[] topics, int[] qos) throws MqttException {
		checkTopicAndQos(topics, qos);
		if (!Objects.isNull(client)) {
			client.subscribe(topics, qos, null, new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken iMqttToken) {
					log.info("【Paho-V3】 => MQTT订阅成功，主题: {}", String.join(",", topics));
				}
				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					log.error("【Paho-V3】 => MQTT订阅失败，主题：{}，错误信息：{}", String.join(",", topics), exception.getMessage(),
							exception);
				}

			});
		}
	}

	public void unsubscribe(String[] topics) throws MqttException {
		checkTopic(topics);
		if (!Objects.isNull(client)) {
			client.unsubscribe(topics, null, new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					log.info("【Paho-V3】 => MQTT取消订阅成功，主题：{}", String.join(",", topics));
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					log.error("【Paho-V3】 => MQTT取消订阅失败，主题：{}，错误信息：{}", String.join(",", topics), exception.getMessage(),
							exception);
				}

			});
		}
	}

	public void publish(String topic, byte[] payload, int qos) throws MqttException {
		if (!Objects.isNull(client)) {
			client.publish(topic, payload, qos, false);
		}
	}

	public void publish(String topic, byte[] payload) throws MqttException {
		publish(topic, payload, pahoProperties.getPublishQos());
	}

	private MqttConnectOptions options() {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setUserName(pahoProperties.getUsername());
		options.setPassword(pahoProperties.getPassword().toCharArray());
		options.setWill("will/topic", WILL_PAYLOAD, pahoProperties.getWillQos(), false);
		// 超时时间
		options.setConnectionTimeout(pahoProperties.getConnectionTimeout());
		// 会话心跳
		options.setKeepAliveInterval(pahoProperties.getKeepAliveInterval());
		// 开启重连
		options.setAutomaticReconnect(pahoProperties.isAutomaticReconnect());
		return options;
	}

	private void connect() throws MqttException {
		client.setManualAcks(pahoProperties.isManualAcks());
		client.setCallback(new PahoMqttClientMessageCallbackV3(messageHandlers));
		client.connect(options(), null, new IMqttActionListener() {
			@Override
			public void onSuccess(IMqttToken asyncActionToken) {
				log.info("【Paho-V3】 => MQTT连接成功，客户端ID：{}", pahoProperties.getClientId());
                try {
                    subscribe(pahoProperties.getTopics().toArray(String[]::new), (pahoProperties.getTopics().stream().mapToInt(item -> pahoProperties.getSubscribeQos()).toArray()));
                } catch (MqttException e) {
                    log.error("【Paho-V3】 => MQTT订阅失败，错误信息：{}", e.getMessage(), e);
                }
            }

			@Override
			public void onFailure(IMqttToken asyncActionToken, Throwable e) {
				log.error("【Paho-V3】 => MQTT连接失败，客户端ID：{}，错误信息：{}", pahoProperties.getClientId(), e.getMessage(), e);
			}
		});
	}

	private void checkTopicAndQos(String[] topics, int[] qosArray) {
		if (topics == null || qosArray == null) {
			throw new IllegalArgumentException("【" + "Paho-V3" + "】 => Topics and QoS arrays cannot be null");
		}
		if (topics.length != qosArray.length) {
			throw new IllegalArgumentException("【" + "Paho-V3" + "】 => Topics and QoS arrays must have the same length");
		}
		if (topics.length == 0) {
			throw new IllegalArgumentException("【" + "Paho-V3" + "】 => Topics array cannot be empty");
		}
	}

	private void checkTopic(String[] topics) {
		if (topics.length == 0) {
			throw new IllegalArgumentException("【" + "Paho-V3" + "】 => Topics array cannot be empty");
		}
	}

}

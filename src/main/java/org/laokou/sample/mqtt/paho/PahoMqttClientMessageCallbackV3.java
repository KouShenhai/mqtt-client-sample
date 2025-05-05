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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.laokou.sample.mqtt.handler.MessageHandler;

import java.util.List;

/**
 * @author laokou
 */
@Slf4j
@RequiredArgsConstructor
public class PahoMqttClientMessageCallbackV3 implements MqttCallback {

	private final List<MessageHandler> messageHandlers;

	@Override
	public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
		log.info("【Paho-V3】 => MQTT消息发送成功，消息ID：{}", iMqttDeliveryToken.getMessageId());
	}

	@Override
	public void connectionLost(Throwable throwable) {
		log.error("【Paho-V3】 => MQTT关闭连接");
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		for (MessageHandler messageHandler : messageHandlers) {
			if (messageHandler.isSubscribe(topic)) {
				log.info("【Paho-V3】 => MQTT接收到消息，Topic：{}", topic);
				messageHandler.handle(new org.laokou.sample.mqtt.handler.MqttMessage(message.getPayload(), topic));
			}
		}
	}
}

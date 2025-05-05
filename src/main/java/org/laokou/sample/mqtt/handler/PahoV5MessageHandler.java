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

package org.laokou.sample.mqtt.handler;

import lombok.extern.slf4j.Slf4j;
import org.laokou.sample.mqtt.util.TopicUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * @author laokou
 */
@Slf4j
@Component
class PahoV5MessageHandler implements MessageHandler {

	@Override
	public boolean isSubscribe(String topic) {
		return TopicUtils.match("/test-topic-3/#", topic);
	}

	@Async
	@Override
	public void handle(MqttMessage mqttMessage) {
		try {
			log.info("【Paho-V5】 => 接收到MQTT消息，topic: {}, message: {}", mqttMessage.getTopic(),
					new String(mqttMessage.getPayload(), StandardCharsets.UTF_8));
		}
		catch (Exception e) {
			log.error("【Paho-V5】 => MQTT消息处理失败，Topic：{}，错误信息：{}", mqttMessage.getTopic(), e.getMessage(), e);
		}
	}

}

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

package org.laokou.sample.mqtt.hivemq;

import com.hivemq.client.mqtt.MqttClientConnectionConfig;
import com.hivemq.client.mqtt.MqttClientExecutorConfig;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.laokou.sample.mqtt.handler.MessageHandler;
import org.laokou.sample.mqtt.handler.MqttMessage;
import org.laokou.sample.mqtt.util.ThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author laokou
 */
@Slf4j
public class HivemqClientV3 {

    /**
     * 服务下线数据.
     */
    private final byte[] WILL_PAYLOAD = "offline".getBytes(UTF_8);

    private final HivemqProperties hivemqProperties;

    private final List<MessageHandler> messageHandlers;

    private volatile Mqtt3RxClient client;

    private final Object lock = new Object();

    private volatile Disposable connectDisposable;

    private volatile Disposable subscribeDisposable;

    private volatile Disposable unSubscribeDisposable;

    private volatile Disposable publishDisposable;

    private volatile Disposable disconnectDisposable;

    private volatile Disposable consumeDisposable;

    public HivemqClientV3(HivemqProperties hivemqProperties, List<MessageHandler> messageHandlers) {
        this.hivemqProperties = hivemqProperties;
        this.messageHandlers = messageHandlers;
    }

    public void open() {
        if (Objects.isNull(client)) {
            synchronized (lock) {
                if (Objects.isNull(client)) {
                    client = getMqtt3ClientBuilder().buildRx();
                }
            }
        }
        connect();
        consume();
    }

    public void close() {
        if (!Objects.isNull(client)) {
            disconnectDisposable = client.disconnect()
                    .subscribeOn(Schedulers.io())
                    .retryWhen(errors -> errors.scan(1, (retryCount, error) -> retryCount > 5 ? -1 : retryCount + 1)
                            .takeWhile(retryCount -> retryCount != -1)
                            .flatMap(retryCount -> Flowable.timer((long) Math.pow(2, retryCount) * 100, TimeUnit.MILLISECONDS)))
                    .subscribe(() -> log.info("【Hivemq-V3】 => MQTT断开连接成功，客户端ID：{}", hivemqProperties.getClientId()),
                            e -> log.error("【Hivemq-V3】 => MQTT断开连接失败，错误信息：{}", e.getMessage(), e));
        }
    }

    public void subscribe() {
        String[] topics = getTopics();
        subscribe(topics, getQosArray(topics));
    }

    public String[] getTopics() {
        return hivemqProperties.getTopics().toArray(String[]::new);
    }

    public int[] getQosArray(String[] topics) {
        return Stream.of(topics).mapToInt(item -> hivemqProperties.getSubscribeQos()).toArray();
    }

    public void subscribe(String[] topics, int[] qosArray) {
        checkTopicAndQos(topics, qosArray);
        if (!Objects.isNull(client)) {
            List<Mqtt3Subscription> subscriptions = new ArrayList<>(topics.length);
            for (int i = 0; i < topics.length; i++) {
                subscriptions.add(Mqtt3Subscription.builder()
                        .topicFilter(topics[i])
                        .qos(getMqttQos(qosArray[i]))
                        .build());
            }
            subscribeDisposable = client.subscribeWith()
                    .addSubscriptions(subscriptions)
                    .applySubscribe()
                    .subscribeOn(Schedulers.io())
                    .retryWhen(errors -> errors.scan(1, (retryCount, error) -> retryCount > 5 ? -1 : retryCount + 1)
                            .takeWhile(retryCount -> retryCount != -1)
                            .flatMap(retryCount -> Flowable.timer((long) Math.pow(2, retryCount) * 100, TimeUnit.MILLISECONDS)))
                    .subscribe(ack -> log.info("【Hivemq-V3】 => MQTT订阅成功，主题: {}", String.join("、", topics)), e -> log
                            .error("【Hivemq-V3】 => MQTT订阅失败，主题：{}，错误信息：{}", String.join("、", topics), e.getMessage(), e));
        }
    }

    public void unSubscribe() {
        String[] topics = hivemqProperties.getTopics().toArray(String[]::new);
        unSubscribe(topics);
    }

    public void unSubscribe(String[] topics) {
        checkTopic(topics);
        if (!Objects.isNull(client)) {
            List<MqttTopicFilter> matchedTopics = new ArrayList<>(topics.length);
            for (String topic : topics) {
                matchedTopics.add(MqttTopicFilter.of(topic));
            }
            unSubscribeDisposable = client.unsubscribeWith()
                    .addTopicFilters(matchedTopics)
                    .applyUnsubscribe()
                    .subscribeOn(Schedulers.io())
                    .retryWhen(errors -> errors.scan(1, (retryCount, error) -> retryCount > 5 ? -1 : retryCount + 1)
                            .takeWhile(retryCount -> retryCount != -1)
                            .flatMap(retryCount -> Flowable.timer((long) Math.pow(2, retryCount) * 100, TimeUnit.MILLISECONDS)))
                    .subscribe(() -> log.info("【Hivemq-V3】 => MQTT取消订阅成功，主题：{}", String.join("、", topics)), e -> log
                            .error("【Hivemq-V3】 => MQTT取消订阅失败，主题：{}，错误信息：{}", String.join("、", topics), e.getMessage(), e));
        }
    }

    public void publish(String topic, byte[] payload, int qos) {
        if (!Objects.isNull(client)) {
            publishDisposable = client
                    .publish(Flowable.just(Mqtt3Publish.builder()
                            .topic(topic)
                            .qos(getMqttQos(qos))
                            .payload(payload)
                            .retain(hivemqProperties.isRetain())
                            .build()))
                    .subscribeOn(Schedulers.io())
                    .retryWhen(errors -> errors.scan(1, (retryCount, error) -> retryCount > 5 ? -1 : retryCount + 1)
                            .takeWhile(retryCount -> retryCount != -1)
                            .flatMap(retryCount -> Flowable.timer((long) Math.pow(2, retryCount) * 100, TimeUnit.MILLISECONDS)))
                    .subscribe(ack -> log.info("【Hivemq-V3】 => MQTT消息发布成功，topic：{}", topic),
                            e -> log.error("【Hivemq-V3】 => MQTT消息发布失败，topic：{}，错误信息：{}", topic, e.getMessage(), e));
        }
    }

    public void publish(String topic, byte[] payload) {
        publish(topic, payload, hivemqProperties.getPublishQos());
    }

    public void dispose(Disposable disposable) {
        if (!Objects.isNull(disposable) && !disposable.isDisposed()) {
            // 显式取消订阅
            disposable.dispose();
        }
    }

    public void dispose() {
        dispose(connectDisposable);
        dispose(subscribeDisposable);
        dispose(unSubscribeDisposable);
        dispose(publishDisposable);
        dispose(consumeDisposable);
        dispose(disconnectDisposable);
    }

    public void reSubscribe() {
        log.info("【Hivemq-V3】 => MQTT重新订阅开始");
        dispose(subscribeDisposable);
        subscribe();
        log.info("【Hivemq-V3】 => MQTT重新订阅结束");
    }

    private MqttQos getMqttQos(int qos) {
        return MqttQos.fromCode(qos);
    }

    private void connect() {
        connectDisposable = client.connectWith()
                .keepAlive(hivemqProperties.getKeepAliveInterval())
                .willPublish()
                .topic("will/topic")
                .payload(WILL_PAYLOAD)
                .qos(getMqttQos(hivemqProperties.getWillQos()))
                .retain(true)
                .applyWillPublish()
                .restrictions()
                .sendMaximum(hivemqProperties.getSendMaximum())
                .sendMaximumPacketSize(hivemqProperties.getSendMaximumPacketSize())
                .applyRestrictions()
                .applyConnect()
                .toFlowable()
                .firstElement()
                .subscribeOn(Schedulers.io())
                .retryWhen(errors -> errors.scan(1, (retryCount, error) -> retryCount > 5 ? -1 : retryCount + 1)
                        .takeWhile(retryCount -> retryCount != -1)
                        .flatMap(retryCount -> Flowable.timer((long) Math.pow(2, retryCount) * 100, TimeUnit.MILLISECONDS)))
                .subscribe(
                        ack -> log.info("【Hivemq-V3】 => MQTT连接成功，主机：{}，端口：{}，客户端ID：{}", hivemqProperties.getHost(),
                                hivemqProperties.getPort(), hivemqProperties.getClientId()),
                        e -> log.error("【Hivemq-V3】 => MQTT连接失败，错误信息：{}", e.getMessage(), e));
    }

    private void consume() {
        if (!Objects.isNull(client)) {
            consumeDisposable = client.publishes(MqttGlobalPublishFilter.ALL)
                    .onBackpressureBuffer(8192)
                    .observeOn(Schedulers.computation(), false, 8192)
                    .doOnSubscribe(subscribe -> {
                        log.info("【Hivemq-V3】 => MQTT开始订阅消息，请稍候。。。。。。");
                        reSubscribe();
                    })
                    .subscribeOn(Schedulers.io())
                    .retryWhen(errors -> errors.scan(1, (retryCount, error) -> retryCount > 5 ? -1 : retryCount + 1)
                            .takeWhile(retryCount -> retryCount != -1)
                            .flatMap(retryCount -> Flowable.timer((long) Math.pow(2, retryCount) * 100, TimeUnit.MILLISECONDS)))
                    .subscribe(publish -> {
                                for (MessageHandler messageHandler : messageHandlers) {
                                    if (messageHandler.isSubscribe(publish.getTopic().toString())) {
                                        log.info("【Hivemq-V3】 => MQTT接收到消息，Topic：{}", publish.getTopic());
                                        messageHandler
                                                .handle(new MqttMessage(publish.getPayloadAsBytes(), publish.getTopic().toString()));
                                    }
                                }
                            }, e -> log.error("【Hivemq-V3】 => MQTT消息处理失败，错误信息：{}", e.getMessage(), e),
                            () -> log.info("【Hivemq-V3】 => MQTT订阅消息结束，请稍候。。。。。。"));
        }
    }

    private Mqtt3ClientBuilder getMqtt3ClientBuilder() {
        Mqtt3ClientBuilder builder = Mqtt3Client.builder().addConnectedListener(listener -> {
                    Optional<? extends MqttClientConnectionConfig> config = Optional
                            .of(listener.getClientConfig().getConnectionConfig())
                            .get();
                    config.ifPresent(mqttClientConnectionConfig -> log.info("【Hivemq-V5】 => MQTT连接保持时间：{}ms",
                            mqttClientConnectionConfig.getKeepAlive()));
                    log.info("【Hivemq-V3】 => MQTT已连接，客户端ID：{}", hivemqProperties.getClientId());
                })
                .addDisconnectedListener(
                        listener -> log.error("【Hivemq-V3】 => MQTT已断开连接，客户端ID：{}", hivemqProperties.getClientId()))
                .identifier(hivemqProperties.getClientId())
                .serverHost(hivemqProperties.getHost())
                .serverPort(hivemqProperties.getPort())
                .executorConfig(MqttClientExecutorConfig.builder()
                        .nettyExecutor(ThreadUtils.newVirtualTaskExecutor())
                        .nettyThreads(hivemqProperties.getNettyThreads())
                        .applicationScheduler(Schedulers.from(ThreadUtils.newVirtualTaskExecutor()))
                        .build());
        // 开启重连
        if (hivemqProperties.isAutomaticReconnect()) {
            builder.automaticReconnect()
                    .initialDelay(hivemqProperties.getAutomaticReconnectInitialDelay(), TimeUnit.SECONDS)
                    .maxDelay(hivemqProperties.getAutomaticReconnectMaxDelay(), TimeUnit.SECONDS)
                    .applyAutomaticReconnect();
        }
        if (hivemqProperties.isAuth()) {
            builder.simpleAuth()
                    .username(hivemqProperties.getUsername())
                    .password(hivemqProperties.getPassword().getBytes())
                    .applySimpleAuth();
        }
        return builder;
    }

    private void checkTopicAndQos(String[] topics, int[] qosArray) {
        if (topics == null || qosArray == null) {
            throw new IllegalArgumentException("【" + "Hivemq" + "】 => Topics and QoS arrays cannot be null");
        }
        if (topics.length != qosArray.length) {
            throw new IllegalArgumentException("【" + "Hivemq" + "】 => Topics and QoS arrays must have the same length");
        }
        if (topics.length == 0) {
            throw new IllegalArgumentException("【" + "Hivemq" + "】 => Topics array cannot be empty");
        }
    }

    private void checkTopic(String[] topics) {
        if (topics.length == 0) {
            throw new IllegalArgumentException("【" + "Hivemq" + "】 => Topics array cannot be empty");
        }
    }

}

package com.happytime188.compose.mqtt.gateway;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


/**
 * Created by pengcheng.du on 2018/10/12.
 */
@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
@Component
public interface MqttGateway {
    void sendToMqtt(String data, @Header(MqttHeaders.TOPIC) String topic);
}


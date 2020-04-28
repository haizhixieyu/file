package com.happytime188.compose.mqtt.controller;

import com.happytime188.compose.mqtt.gateway.MqttGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private MqttGateway mqttGateway;

    @Value("${spring.mqtt.topic}")
    private String topic;

    @RequestMapping("/sendMqtt.do")
    public String sendMqtt(String  sendData){
        mqttGateway.sendToMqtt(sendData,topic);
        return "OK";
    }
}
package com.happytime188.compose;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.happytime188.cabinet.bean.Cabinet;
import com.happytime188.compose.bean.DispatchMsg;
import com.happytime188.compose.bean.MqttMap;
import com.happytime188.compose.feign.CabinetFeignClient;
import com.happytime188.compose.feign.ToolFeignClient;
import com.happytime188.compose.service.tools.ToolsService;
import com.happytime188.compose.util.EncryptionUtils;
import com.happytime188.tools.util.JsonMsg;
import com.happytime188.tools.util.ObjMsg;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

import java.util.Map;


/**
 * 〈一句话功能简述〉<br>
 * 〈MQTT发送消息配置〉
 *
 * @author AnswerChang
 * @create 2018/6/4
 * @since 1.0.0
 */
@Configuration
@IntegrationComponentScan
public class MqttSenderConfig {

    @Value("${spring.mqtt.username}")
    private String username;

    @Value("${spring.mqtt.password}")
    private String password;

    @Value("${spring.mqtt.url}")
    private String hostUrl;

    @Value("${spring.mqtt.client.id}")
    private  String clientId;

    @Value("${spring.mqtt.default.topic}")
    private String defaultTopic;

    @Value("${spring.mqtt.completionTimeout}")
    private int completionTimeout;   //连接超时

    @Value("${spring.mqtt.topic}")
    private String topic;

    private static String time;

    private final RedisTemplate redisTemplate;

    private final ToolsService toolsService;

    private static Gson gson = new Gson();

    static {
       String nowTime= String.valueOf(System.currentTimeMillis());
       time=nowTime.substring(nowTime.length()-5);
    }

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(MqttSenderConfig.class);

    public MqttSenderConfig(RedisTemplate redisTemplate, ToolsService toolsService) {
        this.redisTemplate = redisTemplate;
        this.toolsService = toolsService;
    }

    @Bean
    public MqttConnectOptions getMqttConnectOptions() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setUserName(username);
        mqttConnectOptions.setPassword(password.toCharArray());
        mqttConnectOptions.setServerURIs(new String[]{hostUrl});
        mqttConnectOptions.setKeepAliveInterval(2);
        return mqttConnectOptions;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(getMqttConnectOptions());
        return factory;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        clientId+="_"+time;
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(clientId, mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(defaultTopic);
        messageHandler.setDefaultQos(1);
        return messageHandler;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }


    //接收通道
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    //配置client,监听的topic
    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "_inbound", mqttClientFactory(),
                        topic);
        adapter.setCompletionTimeout(completionTimeout);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    //通过通道获取数据
    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
//                String topic = message.getHeaders().get("mqtt_receivedTopic").toString();
//                String type = topic.substring(topic.lastIndexOf("/") + 1, topic.length());
                String jsonMsg = message.getPayload().toString();
                log.error("收到Json消息:" + jsonMsg);
                try {
                    DispatchMsg dispatchMsg = gson.fromJson(jsonMsg, DispatchMsg.class);
                    String body = dispatchMsg.getBody();
                    switch (dispatchMsg.getHead()) {
                        case "OPENLOCK":
                            openLock(body,dispatchMsg.getMqttId());
                            break;
                        case "NOTICE":
                            notice(body);
                            break;
                        case "DROP":
                            drop(body);
                            break;
                        case "UPLOAD":
//                            upload(body);
                            break;
                        case "CLOSELOCK":
                            closeLock(body);
                            break;
                        case "LOCKSTATUS":
                            lockStatus(body, dispatchMsg.getMqttId());
                            break;
                        case "PULSE":
//                            clientPulse(body, ctx);
                            break;
                        case "VERIFYDEVICE":
                            getVerifyValue(body, dispatchMsg.getMqttId());
                            break;
                        case "CHECK_VERSION":
                            getCheckVersion(body);
                            break;
                        case "REBOOT":
                            getReboot(body);
                            break;
                        default:
                            other(body);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }


    private void notice(String msgBody) {
        String[] tempJson = EncryptionUtils.base64DecodeStr(msgBody).split("&");
        String deviceNo=tempJson[0];
        String clientVersion=tempJson[1];
        log.info("柜子端上线通知:" + deviceNo+"----当前版本:"+clientVersion);
        redisTemplate.boundHashOps("deviceStatus").put(deviceNo, "notice");
    }

    private void drop(String msgBody) {
        String[] tempJson = EncryptionUtils.base64DecodeStr(msgBody).split("&");
        String deviceNo=tempJson[0];
        String clientVersion=tempJson[1];
        log.info("柜子端:" + deviceNo + "已掉线----当前版本:"+clientVersion);
        redisTemplate.boundHashOps("deviceStatus").put(deviceNo, "drop");
    }

    private void openLock(String msgBody,String mqttId) {
        String tempJson = EncryptionUtils.base64DecodeStr(msgBody);
        log.error("开锁信息:" + tempJson);
        try {
            MqttMap.mattMap.put(mqttId, tempJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeLock(String msgBody) {
        String tempJson = EncryptionUtils.base64DecodeStr(msgBody);

        log.error("关锁信息:" + tempJson);
    }


    private void lockStatus(String msgBody, String mqttId) {
        String tempJson = EncryptionUtils.base64DecodeStr(msgBody);
        log.error("锁状态返回信息:" + tempJson);
        try {
            MqttMap.mattMap.put(mqttId, tempJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getVerifyValue(String msgBody, String mqttId) {
        String tempJson = EncryptionUtils.base64DecodeStr(msgBody);
        log.error("设备值:" + tempJson);
        try {
            MqttMap.mattMap.put(mqttId, tempJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void getCheckVersion(String msgBody) {
        String tempJson = EncryptionUtils.base64DecodeStr(msgBody);
        Map<String,String> map = gson.fromJson(tempJson, Map.class);
        log.info("当前版本："+map.get("clientVersion")+"检查更新");
        toolsService.checkVersion(map.get("appId"), map.get("clientType"), map.get("clientVersion"), map.get("deviceNo"));
    }

    private void getReboot(String msgBody) {
        String tempJson = EncryptionUtils.base64DecodeStr(msgBody);
        Map<String,String> map = gson.fromJson(tempJson, Map.class);
        log.info("设备重启："+map.get("clientVersion")+"检查更新");
        toolsService.checkVersion(map.get("appId"), map.get("clientType"), map.get("clientVersion"), map.get("deviceNo"));
    }

    private void other(String msgBody) {
        String tempJson = EncryptionUtils.base64DecodeStr(msgBody);

        log.error("打印其它信息:" + tempJson);
    }


}


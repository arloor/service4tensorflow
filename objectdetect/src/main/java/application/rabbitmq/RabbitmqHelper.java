package application.rabbitmq;

import application.model.Status;
import application.service.DetectService;
import application.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Component
public class RabbitmqHelper implements RabbitTemplate.ConfirmCallback {

    private final Logger logger=LoggerFactory.getLogger(RabbitmqHelper.class);

    @Autowired
    DetectService detectService;

    private String lastMegTime;
    private String lastMsg;

    private String host;
    private String IP;
    @Value("${server.port}")
    String port;
    private final AmqpAdmin amqpAdmin;
    private ConnectionFactory connectionFactory;

    private static String FAOUT_EXCHANGE="ob-ctrl-faout";

    private static String DIRECT_EXCHANGE="spring-boot-direct-key-ob";

    private static String ROUTINGKEY="ob";

    public static String QUEUE_NAME;


    private RabbitTemplate rabbitTemplate;

    ObjectMapper ow=new ObjectMapper();



    @Autowired
    public RabbitmqHelper(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate,ConnectionFactory connectionFactory) {
        try {
            InetAddress ia = InetAddress.getLocalHost();
            this.host = ia.getHostName();//获取计算机主机名
            this.IP= ia.getHostAddress();//获取计算机IP

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory=connectionFactory;
        rabbitTemplate.setConfirmCallback(this); //rabbitTemplate如果为单例的话，那回调就是最后设置的内容
    }

    /**
     * 针对消费者配置
     FanoutExchange: 将消息分发到所有的绑定队列，无routingkey的概念
     HeadersExchange ：通过添加属性key-value匹配
     DirectExchange:按照routingkey分发到指定队列
     TopicExchange:多关键字匹配
     */
//    @Bean
//    public DirectExchange directExchange() {
//        return new DirectExchange(DIRECT_EXCHANGE);
//    }

    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FAOUT_EXCHANGE);
    }



    @Bean
    public Queue queue() {
        QUEUE_NAME="ob-"+host+"-"+IP+"-"+port;
        return new Queue(QUEUE_NAME, true); //队列持久

    }

    @Bean
    public Binding binding() {
        logger.info("binding 订阅FAOUT_EXCHANGE");
        return BindingBuilder.bind(queue()).to(fanoutExchange());
    }

    @Bean
    public SimpleMessageListenerContainer messageContainer() {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueues(queue());
        container.setExposeListenerChannel(true);
        container.setMaxConcurrentConsumers(1);
        container.setConcurrentConsumers(1);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL); //设置确认模式手工确认
        container.setMessageListener(new ChannelAwareMessageListener() {

            @Override
            public void onMessage(Message message, Channel channel) throws Exception {
                byte[] body = message.getBody();
                String queue_msg=new String(body);
                String queueName=queue_msg.substring(0,queue_msg.indexOf("::"));
                String msg=queue_msg.substring(queue_msg.indexOf("::")+2);
                logger.info("收到消息   "+queue_msg);
                processMeg(queueName,msg);

                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false); //确认消息成功消费
            }

            private void processMeg(String queueName, String msg) {
                //todo

                //msg= "ob-"+host+"-"+IP+"-"+port+"::"+order
                if(msg.startsWith(QUEUE_NAME)||msg.startsWith("all")){
                    String order=msg.substring(msg.indexOf("::")+2);
                    if(order.equals("status")){
                        //获取本身状态，发送
                        Status status=detectService.staus(staus(new Status()));
                        try {
                            String tosendMsg=ow.writeValueAsString(status);
                            tosendMsg=tosendMsg.replaceAll("\"","'");
                            send("status::"+tosendMsg);
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        return container;
    }

    public void send(String msg){
        CorrelationData ID=new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(DIRECT_EXCHANGE,"ob",QUEUE_NAME+"::"+msg,ID);
        logger.info("发送消息:  "+msg +"，ID为： "+ID);
        lastMegTime= Utils.getFormedDate();
        lastMsg=msg;
        }
    /**
     * 回调
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            logger.info("——消息消费成功 id:" + correlationData);
        } else {
            logger.info("——消息消费失败 id:" + correlationData);
        }
    }

    public Status staus(Status status){
        status.setIP(IP);
        status.setPort(port);
        status.setNodeName(QUEUE_NAME);
        status.setLastMsgTime(this.lastMegTime);
        return status;
    }
}

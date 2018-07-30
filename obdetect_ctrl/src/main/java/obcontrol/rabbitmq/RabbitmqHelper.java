package obcontrol.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import obcontrol.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RabbitmqHelper implements RabbitTemplate.ConfirmCallback {

    private final Logger logger=LoggerFactory.getLogger(RabbitmqHelper.class);

    private final AmqpAdmin amqpAdmin;


    private ConnectionFactory connectionFactory;

    private static String FAOUT_EXCHANGE="ob-ctrl-faout";


    public final static String QUEUE_NAME="ob-ctrl";

    private static String DIRECT_EXCHANGE="spring-boot-direct-key-ob";

    private static String DIRECT_KEY="ob";


    private RabbitTemplate rabbitTemplate;

    Map<String,Status> nodeStatusMap=new ConcurrentHashMap<>();

    ObjectMapper ow=new ObjectMapper();

    public Map<String, Status> getNodeStatusMap() {
        return nodeStatusMap;
    }


    @Autowired
    public RabbitmqHelper(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate,ConnectionFactory connectionFactory) {

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
    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(DIRECT_EXCHANGE);
    }


    @Bean
    public FanoutExchange fanoutExchange() {
        return new FanoutExchange(FAOUT_EXCHANGE);
    }



    @Bean
    public Queue queue() {
        return new Queue(QUEUE_NAME, true); //队列持久

    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(directExchange()).with(DIRECT_KEY);
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

                //如果为报告状态
                if(msg.startsWith("status")){
                    String statusJson=msg.substring(msg.indexOf("::")+2);
                    try {
                        statusJson=statusJson.replaceAll("'","\"");
                        statusJson=statusJson.replaceAll("null","\"\"");
                        Status status=ow.readValue(statusJson,Status.class);
                        //使用map保存状态
                        nodeStatusMap.put(queueName,status);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        });
        return container;
    }

    public void send(String msg){
        CorrelationData ID=new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(FAOUT_EXCHANGE,"ob",QUEUE_NAME+"::"+msg,ID);
        logger.info("发送消息:  "+msg +"，ID为： "+ID);
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

    public Map<String,Status> deleteNodeInfo(String nodeName){
         nodeStatusMap.remove(nodeName);
        return nodeStatusMap;
    }
}

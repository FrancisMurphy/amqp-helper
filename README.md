# amqp-helper
一个动态初始化amqp配置的小工具,为降低用于对交换机、队列初始化绑定初始化大量的配置代码，此小工具会基于一定配置规则对amqp的交换机、队列进行动态绑定并且注册；

## 更新状态
已支持springboot spi机制，客户端对amqp相关bean的注入无感知

### 解析规则
```java
/**
 * 负责通用化的处理MQ配置与初始化,管理器会将配置信息合法的mq注册至容器,根据交换机名称前缀来实例话对应类型的交换机
 * exchange.fanout.xxxxx.result
 *   [1]     [2]   [...]   [n]
 * n >= 3
 * [1]:确认是交换机名称
 * [2]:获取交换机类型:fanout,direct
 * 目前只支持fanout与direct类型的交换机，队列的名称解析规则通过此处解析出的交换机类型来进行解析
 *
 * fanout交换机的队列命名规则:
 * 单个队列的命名规则:
 * queueName
 *
 * direct交换机的队列命名规则:
 * 单个队列的命名格式:
 * queueName     :     routingKeyName1   &   routingKeyName2
 * [1]          [2]         [3]         [4]        [5]         [...]
 * [1]队列名
 * [2]区分队列名与routingKey的分隔符
 * [3]第一个需要建立绑定关系的routingKeyName
 * [4]区分多个routingKey的分隔符
 * [5]第二个需要建立绑定关系的routingKeyName
 *
 * e.g: queue.loan:routing.key.risk&routing.key.admin
 *
 * 如单个交换机下绑定多个queue，则用","分隔
 * e.g:queueDefine1,queueDefine2,queueDefine3
 *
 * @author frank
 */
```

### 使用方式：
  
#### Step1
实现com.frank.amqp.helper.config.ICommonMqConfig接口，将配置的交换机、队列、路由信息返回，并将实现了此接口的实现类注入到容器中；  
比如：  
```java
@Configuration  
public class CommonMqConfig implements ICommonMqConfig  
{  
    public final static String fanoutExchangeName = "exchange.fanout.test";  
    public final static String fanoutQueueDefines = "fanout.queue.test1,fanout.queue.test2";  
    public final static String directExchangName = "exchange.direct.test";  
    public final static String directQueueDefines = "direct.queue.test1:routingKey.test1,direct.queue.test2:routingKey.test2&routingKey.test3";  
    public final static String testRoutingKey = "routingKey.test1";  
    public final static String fanoutQueueName1 = "fanout.queue.test1";  
    public final static String fanoutQueueName2 = "fanout.queue.test2";  
    public final static String directQueueName = "direct.queue.test1";  
    private Map<String,String> configMq = new HashMap<>();  
  
    @Override  
    public Map<String, String> getConfig()  
    {  
        configMq.put(fanoutExchangeName,fanoutQueueDefines);  
        configMq.put(directExchangName,directQueueDefines);  
        return configMq;  
    }  
}  
```

#### Step2
Amqp的模版可以直接通过原生注入，比如rabbitmq
application.properties:
```xml
spring.rabbitmq.host=your mq host
spring.rabbitmq.port=5672
spring.rabbitmq.username=xxxx
spring.rabbitmq.password=xxxx
spring.rabbitmq.virtual-host=/
```
@Bean注解注入
```java
    public CachingConnectionFactory commonMqConnFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitmqHost);
        connectionFactory.setVirtualHost(rabbitmqVhost);
        connectionFactory.setPort(rabbitmqPort);
        connectionFactory.setUsername(rabbitmqUserName);
        connectionFactory.setPassword(rabbitmqPassword);
        return connectionFactory;
    }

    @Bean
    public RabbitTemplate isvRabbitTemplate(){
        CachingConnectionFactory connectionFactory = commonMqConnFactory();
        connectionFactory.setPublisherConfirms(true);
        connectionFactory.setPublisherReturns(true);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(
                (correlationData, ack, cause) -> log.debug("###CommonMqTemplate### confirmCallback->correlationData({}),ack({}),cause({})",correlationData,ack,cause));
        rabbitTemplate.setReturnCallback(
                (message, replyCode, replyText, exchange, routingKey) -> log.debug("###CommonMqTemplate### returnCallback-> exchange({}),route({}),replyCode({}),replyText({}),message:{}",exchange,routingKey,replyCode,replyText,message));
        return rabbitTemplate;
    }
```





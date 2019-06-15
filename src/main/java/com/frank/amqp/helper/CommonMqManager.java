package com.frank.amqp.helper;

import com.frank.amqp.helper.config.ICommonMqConfig;
import com.frank.amqp.helper.inject.DynamicInjectAssistant;
import com.frank.amqp.helper.parser.CommonMqParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 负责通用化的处理MQ配置与初始化,管理器会将配置信息合法的mq注册至容器,根据交换机名称前缀来实例话对应类型的交换机
 * exchange.fanout.xxxxx.transpay.result
 * [1]     [2]    [...]     [n]
 * n >= 3
 * [1]:确认是交换机名称
 * [2]:获取交换机类型:fanout,direct
 * 目前只支持fanout与direct类型的交换机，队列的名称解析规则通过此处解析出的交换机类型来进行解析
 * <p>
 * fanout交换机的队列命名规则:
 * 单个队列的命名规则:
 * queueName
 * <p>
 * direct交换机的队列命名规则:
 * 单个队列的命名格式:
 * queueName     :     routingKeyName1   &   routingKeyName2
 * [1]        [2]         [3]         [4]        [5]         [...]
 * [1]队列名
 * [2]区分队列名与routingKey的分隔符
 * [3]第一个需要建立绑定关系的routingKeyName
 * [4]区分多个routingKey的分隔符
 * [5]第二个需要建立绑定关系的routingKeyName
 * <p>
 * e.g: queue.loan:routing.key.risk&routing.key.admin
 * <p>
 * 如单个交换机下绑定多个queue，则用","分隔
 * e.g:queueDefine1,queueDefine2,queueDefine3
 *
 * @author frank
 */
@Slf4j
@Component
public class CommonMqManager implements InitializingBean
{
    private ICommonMqConfig commonMqConfig;

    private DynamicInjectAssistant dynamicInjectAssistant;

    /**
     * 经过解析的交换机与队列名称绑定映射
     */
    private Map<String, Map<String, List<String>>> parsedBindDefineMap = new HashMap<>();

    /**
     * 交换机实例与队列实例绑定映射
     */
    private Map<Exchange, List<Queue>> instanceBindMap = new HashMap<>();

    @Autowired
    public CommonMqManager(
            ICommonMqConfig commonMqConfig,
            DynamicInjectAssistant dynamicInjectAssistant)
    {
        this.commonMqConfig = commonMqConfig;
        this.dynamicInjectAssistant = dynamicInjectAssistant;
    }

    @Override
    public void afterPropertiesSet() throws Exception
    {
        init();
    }

    private void init()
    {
        log.debug("###CommonMqManager->init###  start...");
        parseAndInit();
        binding();
        log.debug("###CommonMqManager->init###  end...");
    }

    /**
     * 用于根据CommonMqConfig中的map映射来确定exchange与queue的绑定关系
     * 为了动态支持单exchange
     */
    private void parseAndInit()
    {
        //获取需要解析的交换机与队列的名称映射
        Map<String, String> nameBindMap = commonMqConfig.getConfig();
        if (null == nameBindMap || nameBindMap.isEmpty())
        {
            log.debug(
                    "###CommonMqManager->init###  parse->Can get nameBindMap for mq...");
            return;
        }

        //解析队列名称，并示例化交换机与队列
        nameBindMap.forEach((exchangeName, queueDefines) ->
                parsedBindDefineMap.put(exchangeName,
                        CommonMqParser
                                .parserQueueDefines(queueDefines)));

        parsedBindDefineMap.entrySet().stream()
                .filter(exchangeEntity ->
                {
                    final String exchangeName = exchangeEntity.getKey();
                    final Map<String, List<String>> queueDefineList = exchangeEntity
                            .getValue();

                    if (!CommonMqParser
                            .isExchangeConfigValid(exchangeName,
                                    queueDefineList))
                    {
                        log.debug(
                                "###CommonMqManager->init###  parse->Exchange:{} name is illega, do not init...",
                                exchangeName);
                        return false;
                    }
                    return true;
                }).forEach(exchangeEntity ->
        {
            final String exchangeName = exchangeEntity.getKey();
            final Map<String, List<String>> queueDefineMap = exchangeEntity
                    .getValue();

            String[] exchangeMetaNames = exchangeName.split("\\.");
            final String exchangeType = exchangeMetaNames[1];

            final Exchange exchange = CommonMqParser
                    .initExchange(exchangeName, exchangeType);

            if (null == exchange)
            {
                log.debug(
                        "###CommonMqManager->init###  parse->Exchange:{} can not get exchange instance...",
                        exchangeName);
                return;
            }

            //将交换机实例动态注入到spring容器中
            try
            {
                if (exchange instanceof FanoutExchange)
                {
                    dynamicInjectAssistant
                            .inject(exchange, exchangeName,
                                    FanoutExchange.class);
                }
                else if (exchange instanceof DirectExchange)
                {
                    dynamicInjectAssistant
                            .inject(exchange, exchangeName,
                                    DirectExchange.class);
                }
            }
            catch (IllegalAccessException e)
            {
                log.error(
                        "###CommonMqManager->init###  parse->Exchange:{} can not inject exchange instance, error:",
                        exchangeName, e);
                return;
            }

            log.debug(
                    "###CommonMqManager->init###  parse->Exchange:{} init success!",
                    exchangeName);

            List<Queue> queueList = new LinkedList<>();

            for (Map.Entry<String, List<String>> queueDefine : queueDefineMap
                    .entrySet())
            {
                final String queueName = queueDefine.getKey();

                Queue queue = new Queue(queueName);
                queueList.add(queue);
                try
                {
                    dynamicInjectAssistant
                            .inject(queue, queueName, Queue.class);
                }
                catch (IllegalAccessException e)
                {
                    log.error(
                            "###CommonMqManager->init###  parse->Queue:{} can not inject queue instance, error:",
                            exchangeName, e);
                    return;
                }
                log.debug(
                        "###CommonMqManager->init###  parse->queue:{} init success!",
                        queueName);
            }

            //将对应的exchange与queue注册到nameBindMap
            instanceBindMap.put(exchange, queueList);
        });
    }

    /**
     * 建立绑定关系
     */
    private void binding()
    {
        instanceBindMap.forEach(((exchange, queues) ->
                queues.forEach(queue ->
                        {
                            if (exchange instanceof FanoutExchange)
                            {
                                FanoutExchange fanoutExchange = (FanoutExchange) exchange;
                                Binding binding = BindingBuilder.bind(queue)
                                        .to(fanoutExchange);
                                String bindBeanName =
                                        exchange.getName() + "BindTo" + queue.getName();

                                try
                                {
                                    dynamicInjectAssistant
                                            .inject(binding, bindBeanName,
                                                    Binding.class);
                                }
                                catch (IllegalAccessException e)
                                {
                                    log.debug(
                                            "###CommonMqManager->init###  binding->Exchange:{} Queue:{} can not binding, error:",
                                            exchange.getName(), queue.getName(), e);
                                }
                            }
                            else if (exchange instanceof DirectExchange)
                            {
                                DirectExchange directExchange = (DirectExchange) exchange;

                                BindingBuilder.DirectExchangeRoutingKeyConfigurer directExchangeRoutingKeyConfigurer = BindingBuilder
                                        .bind(queue)
                                        .to(directExchange);

                                final List<String> routingKeys = parsedBindDefineMap
                                        .get(directExchange.getName())
                                        .get(queue.getName());
                                if (null == routingKeys || routingKeys.isEmpty())
                                {
                                    return;
                                }

                                routingKeys.forEach(routingKey ->
                                {
                                    Binding binding = directExchangeRoutingKeyConfigurer
                                            .with(routingKey);
                                    String bindBeanName =
                                            exchange.getName() + "BindTo" +
                                                    queue.getName() + "With" +
                                                    routingKey;
                                    try
                                    {
                                        dynamicInjectAssistant
                                                .inject(binding, bindBeanName,
                                                        Binding.class);
                                    }
                                    catch (IllegalAccessException e)
                                    {
                                        log.debug(
                                                "###CommonMqManager->init###  binding->Exchange:{} Queue:{} can not binding, error:",
                                                exchange.getName(), queue.getName(), e);
                                    }
                                });
                            }
                        }
                ))
        );
    }
}

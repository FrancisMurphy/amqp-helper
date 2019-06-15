package com.frank.amqp.helper.parser;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 基于的amqp的通用化MQ配置解析器
 * 目前交换机类型只支持fanout
 */
public class CommonMqParser
{
    private static Set<String> supportExchangeType = new HashSet<>(2);

    static
    {
        supportExchangeType.add("fanout");
        supportExchangeType.add("direct");
    }

    public static boolean isExchangeTypeValid(String exchangeType)
    {
        return supportExchangeType.contains(exchangeType);
    }

    public static Exchange initExchange(String exchangeName,String exchangeType)
    {
        switch (exchangeType)
        {
            case "fanout":
                return new FanoutExchange(exchangeName);
            case "direct":
                return new DirectExchange(exchangeName);
            default:
                return null;
        }
    }

    public static boolean isExchangeConfigValid(String exchangeName,
            Map<String,List<String>> queueDefineMap)
    {
        String[] exchangeMetaNames = exchangeName.split("\\.");

        if (exchangeMetaNames.length < 3
                || !exchangeMetaNames[0].equals("exchange")
                || !CommonMqParser
                .isExchangeTypeValid(exchangeMetaNames[1])
                || null == queueDefineMap
                || queueDefineMap.isEmpty())
        {
            return false;
        }
        return true;
    }

    public static Map<String,List<String>> parserQueueDefines(
            String queueDefines)
    {
        if (StringUtils.isEmpty(queueDefines))
        {
            return new HashMap<>(0);
        }

        String[] queueDefineArray = queueDefines.split(",");
        Map<String,List<String>> queueDefineMap = new HashMap<>();
        if(0 == queueDefineArray.length)
        {
            return new HashMap<>(0);
        }

        Arrays.stream(queueDefineArray).
                forEach(queueDefine->{
                    String[] queueDefineMeta = queueDefine.split(":");
                    if(2 <= queueDefineMeta.length)
                    {
                        final String queueName = queueDefineMeta[0];
                        final String routingKeys = queueDefineMeta[1];

                        //解析routingKey
                        String[] routingKeyArray = routingKeys.split("&");
                        if(0 == routingKeyArray.length)
                        {
                            return;
                        }

                        List<String> routingKeyList = new ArrayList<>();
                        Collections.addAll(routingKeyList, routingKeyArray);
                        queueDefineMap.put(queueName,routingKeyList);
                    }
                    else if(1 == queueDefineMeta.length)
                    {
                        final String queueName = queueDefineMeta[0];
                        queueDefineMap.put(queueName,null);
                    }
                    else
                    {
                        return;
                    }
                }
                );
        return queueDefineMap;
    }

}

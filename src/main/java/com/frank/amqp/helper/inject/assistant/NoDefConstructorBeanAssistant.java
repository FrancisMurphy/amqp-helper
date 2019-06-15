package com.frank.amqp.helper.inject.assistant;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;

import java.util.Map;

public class NoDefConstructorBeanAssistant
{
    public static boolean dealNoDefConsBean(BeanDefinitionBuilder beanDefinitionBuilder,Class targetClazz,
            Map<String, Object> fieldMap)
    {
        if(targetClazz == FanoutExchange.class
                || targetClazz == DirectExchange.class
                || targetClazz == Queue.class)
        {
            beanDefinitionBuilder.addConstructorArgValue(fieldMap.get("name"));
            return true;
        }
        else if(targetClazz == Binding.class)
        {
            beanDefinitionBuilder.addConstructorArgValue(fieldMap.get("destination"));
            beanDefinitionBuilder.addConstructorArgValue(fieldMap.get("destinationType"));
            beanDefinitionBuilder.addConstructorArgValue(fieldMap.get("exchange"));
            beanDefinitionBuilder.addConstructorArgValue(fieldMap.get("routingKey"));
            beanDefinitionBuilder.addConstructorArgValue(fieldMap.get("arguments"));
            return true;
        }
        return false;
    }
}

package com.frank.amqp.helper.spi;

import com.frank.amqp.helper.CommonMqManager;
import com.frank.amqp.helper.config.ICommonMqConfig;
import com.frank.amqp.helper.inject.ApplicationContextKeeper;
import com.frank.amqp.helper.inject.DynamicInjectAssistant;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomMqAutoConfiguration
{
    @Bean
    public ApplicationContextKeeper applicationContextKeeper()
    {
        return new ApplicationContextKeeper();
    }

    @Bean
    @ConditionalOnClass(value = {ApplicationContextKeeper.class})
    @ConditionalOnMissingBean
    public DynamicInjectAssistant dynamicInjectAssistant()
    {
        return new DynamicInjectAssistant();
    }

    @ConditionalOnClass(value = {AmqpTemplate.class, ICommonMqConfig.class })
    @ConditionalOnMissingBean
    @Bean
    public CommonMqManager commonMqManager(ICommonMqConfig commonMqConfig,
            DynamicInjectAssistant dynamicInjectAssistant)
    {
        return new CommonMqManager(commonMqConfig,dynamicInjectAssistant);
    }
}

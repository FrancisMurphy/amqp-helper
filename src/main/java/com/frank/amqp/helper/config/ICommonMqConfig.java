package com.frank.amqp.helper.config;

import java.util.Map;

/**
 * 用于接受mq配置参数的接口，需要将其注入到容器中
 *
 */
public interface ICommonMqConfig
{
    Map<String,String> getConfig();
}

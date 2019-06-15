package com.frank.amqp.helper.inject;

import com.frank.amqp.helper.inject.assistant.NoDefConstructorBeanAssistant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 动态注入bean
 * 1.通过反射的方式将所有的成员拿出；
 * 2.将已实例化的实例value写入beanDefinitionBuilder中
 * 3.通过registerBeanDefinition,将实例副本注入到spring容器中
 */
@Slf4j
@Component
public class DynamicInjectAssistant implements InitializingBean
{
    private DefaultListableBeanFactory beanFactory;

    private ApplicationContextKeeper applicationContextKeeper;

    @Autowired
    public DynamicInjectAssistant(
            ApplicationContextKeeper applicationContextKeeper)
    {
        this.applicationContextKeeper = applicationContextKeeper;
    }

    @Override
    public void afterPropertiesSet() throws Exception
    {
        ApplicationContext applicationContext = ApplicationContextKeeper
                .getApplicationContext();
        if (null == applicationContext)
        {
            log.debug(
                    "###DynamicInjectAssistant### afterPropertiesSet->Can not get applicationContext...");
            return;
        }

        beanFactory = (DefaultListableBeanFactory) applicationContext
                .getAutowireCapableBeanFactory();
    }

    public <T> void inject(Map<String, T> beanMap, Class<T> beanClazz)
            throws IllegalAccessException
    {
        if (beanFactory == null)
        {
            log.debug(
                    "###DynamicInjectAssistant### inject-> beanFactory is null, so can not dynamic inject bean to container...");
            return;
        }

        for (Map.Entry<String, T> beanEntry : beanMap.entrySet())
        {
            final String beanName = beanEntry.getKey();
            final T bean = beanEntry.getValue();
            inject(bean, beanName, beanClazz);
        }
    }

    /**
     * 单Bean的动态注入
     *
     * @param bean
     * @param beanName
     * @param targetClazz
     * @param <T>
     * @throws IllegalAccessException
     */
    public <T> void inject(Object bean, String beanName,
            Class<T> targetClazz) throws IllegalAccessException
    {
        if (beanFactory == null)
        {
            log.debug(
                    "###DynamicInjectAssistant### inject-> beanFactory is null, so can not dynamic inject bean to container...");
            return;
        }

        //通过放射的方式解析实例成员
        Map<String, Object> fieldMap = new HashMap<>();

        for (Field field : getAllFields(targetClazz))
        {
            boolean flag = field.isAccessible();
            field.setAccessible(true);
            final String fieldName = field.getName();
            Object fieldValue = field.get(bean);
            fieldMap.put(fieldName, fieldValue);
            field.setAccessible(flag);
        }
        //将已实例化的实例value写入beanDefinitionBuilder中
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
                .genericBeanDefinition(targetClazz);
        if(!NoDefConstructorBeanAssistant
                .dealNoDefConsBean(beanDefinitionBuilder,targetClazz,fieldMap))
        {
            fieldMap.forEach(beanDefinitionBuilder::addPropertyValue);
        }
        //通过registerBeanDefinition,将实例副本注入到spring容器中
        beanFactory.registerBeanDefinition(beanName,
                beanDefinitionBuilder.getBeanDefinition());

        log.debug(
                "###DynamicInjectAssistant### inject->Dynamic inject bean:{} class:{} success!",
                beanName, targetClazz);
    }

    private static Field[] getAllFields(Class clazz){
        List<Field> fieldList = new ArrayList<>();
        while (clazz != null){
            fieldList.addAll(new ArrayList<>(Arrays.asList(clazz.getDeclaredFields())));
            clazz = clazz.getSuperclass();
        }
        Field[] fields = new Field[fieldList.size()];
        fieldList.toArray(fields);
        return fields;
    }
}

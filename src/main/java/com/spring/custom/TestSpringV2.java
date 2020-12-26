package com.spring.custom;

import com.spring.custom.ioc.BeanDefinition;
import com.spring.custom.ioc.PropertyValue;
import com.spring.custom.ioc.RuntimeBeanReference;
import com.spring.custom.ioc.TypedStringValue;
import com.mysql.jdbc.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version 1.0
 * @ClassName TestSpringV2
 * @Description 模拟实现spring 获取一个对象  面向过程实现
 * @Author wangdi
 * @Date 2020/12/2 22:55
 **/

// 解决方法：配置文件（注解）+ 反射


public class TestSpringV2 {

    // 存储最终解析出来的BeanDefinition
    private Map<String, BeanDefinition> beanDefinitions = new HashMap<>();

    // 存储单例Bean的集合
    private Map<String, Object> singleToneObjects = new HashMap<>();

    // XML解析
    @Before
    public void before(){
        // 需要配置的内容：类的全路径，属性名称，属性值
        // 1.配置文件
        // <bean class ="类的全路径" scope = "singleton,prototype">
        //  <property name="属性名称" value="属性值"/>
        // </bean>


        // 2.解析配置文件
        // 解析流程（只需要解析一次，就将所有的bean标签封装到对应的对象中（缓存））
        // Dom4j解析---> BeanDefinition(封装bean标签的信息) PropertyValue
        // Map集合（k bean的名字,v bean的definition）


        String path = "beans.xml";
        InputStream inputStream = getResourceAsStream(path);


        // 创建Document
        Document document = getDocument(inputStream);

        // 按照Spring语义解析Document
        parseBeanDefinitions(document.getRootElement());

    }

    /**
     * 功能描述 测试生成对象
     * @author wangdi
     * @date   2020/12/4 23:53
     * @param
     * @return void
     */
    @Test
    public void Test() {
        Object userService = getBean("userService");
        Object dataSource = getBean("dataSource");


    }

    /**
     * 功能描述 解析xml生成beanDefinition
     * @author wangdi
     * @date   2020/12/4 23:32
     * @param rootElement
     * @return void
     */
    private void parseBeanDefinitions(Element rootElement) {
        List<Element> elements = rootElement.elements();

        for (Element element : elements) {
            String name = element.getName();
            if (name.equals("bean")){
                parseDefaultElement(element);
            }else{
                parseCustomElement(element);
            }
        }


    }

    private void parseCustomElement(Element element) {
        return;
    }

    /**
     * 解析bean标签
     * @param element
     */
    private void parseDefaultElement(Element element) {
        String id = element.attributeValue("id");
        String className = element.attributeValue("class");
        String init = element.attributeValue("init-method");
        String scope = element.attributeValue("scope");

        // 获取Class类对象
        try {
            id = id == null || id.equals("") ? Class.forName(className).getSimpleName() : id;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        BeanDefinition beanDefinition = new BeanDefinition(className,id);

        // 默认scope single
        scope = StringUtils.isEmptyOrWhitespaceOnly(scope) ? "singleton" : "prototype";
        beanDefinition.setScope(scope);

        // 装init
        beanDefinition.setInitMethod(init);

        // 装property
        List<Element> elements = element.elements("property");
        List<PropertyValue> objects = new ArrayList<>();
        wrapProperty(elements,objects);
        beanDefinition.setPropertyValues(objects);

        beanDefinitions.put(id,beanDefinition);
    }

    /**
     * 功能描述 包装property
     * @author wangdi
     * @date   2020/12/4 23:47
     * @param elements
     * @param objects
     * @return void
     */
    private void wrapProperty(List<Element> elements, List<PropertyValue> objects) {
        for (Element element : elements) {
            String name = element.attributeValue("name");
            String value = element.attributeValue("value");
            String ref = element.attributeValue("ref");

            // 如果是ref
            if (StringUtils.isNullOrEmpty(value)) {
                RuntimeBeanReference reference = new RuntimeBeanReference(ref);
                PropertyValue propertyValue = new PropertyValue(name,reference);
                objects.add(propertyValue);

            } else {
                TypedStringValue typedStringValue = new TypedStringValue(value);
                PropertyValue propertyValue = new PropertyValue(name,typedStringValue);
                objects.add(propertyValue);
            }
        }
    }


    private Document getDocument(InputStream inputStream) {
        try {
            SAXReader saxReader = new SAXReader();
            return saxReader.read(inputStream);
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return null;
    }

    private InputStream getResourceAsStream(String path) {
        return this.getClass().getClassLoader().getResourceAsStream(path);
    }


    private Object getBean(String name) {

        // 分析： 程序员B写的代码，拓展性不好


        // 3.getBean()流程(针对单例模式Bean进行存储，针对多例不存储)
        // Map 集合（K ：bean名称，V： 单例bean的实例）

        //1. 先查缓存中有没有，
        Object bean = this.singleToneObjects.get(name);


        //2. 有则直接返回
        if (bean != null) {
            return bean;
        }

        //3. 没有则查询对应的BeanDefinition准备创建对象
        BeanDefinition bd = this.beanDefinitions.get(name);
        if (bd == null) {
            return null;
        }
        //4. 判断单例还是多例
        if (bd.isSingleton()) {
            //5. 触发创建单例Bean实例的流程
            bean = createBean(bd);
            //6. 将单例Bean实例放到Map缓存
            singleToneObjects.put(name,bean);

        } else if (bd.isPrototype()) {
            bean = createBean(bd);

        }

        return bean;
    }

    /* *
     * 功能描述 創建對象
     * @author wangdi
     * @date   2020/12/2 23:44
     * @param bd
     * @return java.lang.Object
     */
    private Object createBean(BeanDefinition bd) {
        // 1.Bean的实例化（new对象）
        Object bean = createBeanInstance(bd);
        // 2. 属性填充/依赖注入 setter
        populateBean(bean,bd);


        // 3. Bean的初始化（调用初始化方法）
        initializingBean(bean,bd);

        return bean;
    }

    /**
     * 功能描述 调用初始化方法 （反射）
     * @author wangdi
     * @date   2020/12/4 23:12
     * @param bean
     * @param bd
     * @return void
     */
    private void initializingBean(Object bean, BeanDefinition bd) {
        // TODO Aware接口的处理（在类创建成功之后，去通过Aware接口装饰一个类）

        // TODO BeanPostProcessor接口方法的处理

        // 处理初始化方法
        invokeInitMethod(bean,bd);

    }


    private void invokeInitMethod(Object bean, BeanDefinition bd) {

        // TODO InitializingBean接口的afterProertiesSet方法的处理

        String initMethod = bd.getInitMethod();
        // 判空
        if (initMethod ==null || "".equals(initMethod)) {
            return;
        }
        try {
            Class<?> aClass = bean.getClass();
            Method method = aClass.getMethod(initMethod);
            method.invoke(bean);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    /**
     * 功能描述 属性填充  反射
     * @author wangdi
     * @date   2020/12/3 23:34
     * @param bean
     * @param bd
     * @return void
     */
    private void populateBean(Object bean, BeanDefinition bd) {
        List<PropertyValue> propertyValues = bd.getPropertyValues();
        for (PropertyValue propertyValue : propertyValues) {

            String name = propertyValue.getName();
            Object value = propertyValue.getValue();

            // 处理属性值（有可能是str，有可能是对象）
            Object beanValue = resolveValue(value);

            // 然后再设置属性(反射)
            setBeanProperty(bean,name,beanValue);


        }

    }

    /**
     * 功能描述 设置bean的属性值（反射）
     * @author wangdi
     * @date   2020/12/4 23:21
     * @param bean
     * @param name
     * @param beanValue
     * @return void
     */
    private void setBeanProperty(Object bean, String name, Object beanValue) {
        Class<?> aClass = bean.getClass();
        try {
//            Field field = aClass.getField(name);
            Field field = aClass.getDeclaredField(name);

            field.setAccessible(true);
            field.set(bean,beanValue);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }


    }

    /**
     * 处理属性值
     *
     */
    private Object resolveValue(Object value) {
        Object userValue = null;
        // 字符值类型需要转（也就是基本数据类型要转）
        if (value instanceof TypedStringValue) {
            TypedStringValue typedStringValue = (TypedStringValue) value;
            String typedStringValueValue = typedStringValue.getValue();
            Class<?> targetType = typedStringValue.getTargetType();
            // 为null 就是字符串类型
            if (targetType != null) {
                userValue = changeValueByType(targetType,typedStringValueValue);

            } else {
                userValue = typedStringValueValue;
            }
        }
        // 引用类型直接返回
        else if (value instanceof RuntimeBeanReference) {
            RuntimeBeanReference reference = (RuntimeBeanReference) value;
            String name = reference.getRef();

            // 这里去哪引用的对象
            userValue = getBean(name);


        }
        return userValue;
    }

    /**
     * 功能描述 转成我们需要的类型
     * @author wangdi
     * @date   2020/12/4 23:18
     * @param targetType
     * @param typedStringValueValue
     * @return java.lang.Object
     */
    private Object changeValueByType(Class<?> targetType, String typedStringValueValue) {

        // TODO 后面是有设计模式进行优化
        if (targetType == Integer.class){
            return Integer.parseInt(typedStringValueValue);
        }else if (targetType == String.class){
            return typedStringValueValue;
        }//....
        return null;
    }

    /* *
     * 功能描述 创建bean实例 反射
     * @author wangdi
     * @date   2020/12/3 23:34
     * @param bd
     * @return java.lang.Object
     */
    private Object createBeanInstance(BeanDefinition bd) {
        try {
            Class<?> clazzType = bd.getClazzType();
            Constructor<?> declaredConstructor = clazzType.getDeclaredConstructor();
            return declaredConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;

    }


}

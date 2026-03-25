# Tsuki Framework

## 简介

参考廖雪峰老师的 [summer-framework](https://github.com/michaelliao/summer-framework) 教程开发的一个仿 Spring
简单框架，设计目标如下：

- [x] context模块：实现ApplicationContext容器与Bean的管理；
- [ ] aop模块：实现AOP功能；
- [ ] jdbc模块：实现JdbcTemplate，以及声明式事务管理；
- [ ] web模块：实现Web MVC和REST API；
- [ ] boot模块：实现一个简化版的“Spring Boot”，用于打包运行。

## 进度表

|                进度                |           时间            |  备注   |
|:--------------------------------:|:-----------------------:|:-----:|
| [tsuki-context](./tsuki-context) | 2026-03-14 ~ 2026-03-25 | IoC容器 |
|            tsuki-aop             |      2026-03-26 ~       |       |
|            tsuki-jdbc            |                         |       |
|            tsuki-web             |                         |       |
|            tsuki-boot            |                         |       |

## 模块介绍

### 1. tsuki-context

#### 模块概述

`tsuki-context` 是本框架的核心模块，实现了一个仿 Spring 框架的 IoC（Inversion of Control）容器，负责 Bean
的生命周期管理、依赖注入和自动装配。该模块基于注解驱动的配置方式，自动扫描、识别、创建和管理 Bean 对象。完全是 Spring IoC
容器的真子集。

##### 核心功能点

1. **注解驱动的 Bean 配置**：使用 `@Component`、`@Configuration`、`@Bean` 等注解标注 Bean
2. **依赖注入方式**：支持构造方法注入和属性注入（字段和 setter 方法）
3. **按名称和类型查找 Bean**：提供 `getBean(String name)` 和 `getBean(Class<T> type)` 等查询接口
4. **Bean 生命周期管理**：支持初始化方法（`@PostConstruct`）和销毁方法（`@PreDestroy`）
5. **循环依赖检测**：检测并防止循环依赖的发生
6. **BeanPostProcessor 机制**：允许在 Bean 创建过程中进行拦截和处理
7. **`@Primary` 和 `@Order` 支持**：在多个候选 Bean 时，通过 `@Primary` 指定首选 Bean，通过 `@Order` 控制加载顺序

#### 相比 Spring 的 IoC 容器

| 功能      | 	spring-context                    | 	tsuki-context        |
|---------|------------------------------------|-----------------------|
| IoC容器 	 | 支持BeanFactory和ApplicationContext 	 | 仅支持ApplicationContext |
| 配置方式    | 	支持XML与Annotation 	                | 仅支持Annotation         |
| 扫描方式    | 	支持按包名扫描 	                         | 支持按包名扫描               |
| Bean类型  | 	支持Singleton和Prototype             | 	仅支持Singleton         |
| Bean工厂  | 	支持FactoryBean和@Bean注解             | 	仅支持@Bean注解           |
| 定制Bean  | 	支持BeanPostProcessor               | 	支持BeanPostProcessor  |
| 依赖注入 	  | 支持构造方法、Setter方法与字段                 | 	支持构造方法、Setter方法与字段   |
| 多容器 	   | 支持父子容器                             | 	不支持                  |

#### 核心功能

##### 1. Bean 的自动扫描和识别

框架通过 `@ComponentScan` 注解或自动推导的方式扫描指定包下的类：

- 自动识别 `@Component`、`@Configuration` 等标注的类
- 过滤掉注解类、枚举类、接口、record 类等不可实例化的类型
- 支持通过 `@Import` 手动导入额外的配置类

##### 2. Bean 定义的创建和管理

- **BeanDefinition**：存储 Bean 的元数据信息，包括：
    - Bean 名称、类型、实例
    - 构造方法或工厂方法
    - 初始化和销毁方法
    - `@Primary` 和 `@Order` 标记

##### 3. Bean 的创建流程

框架采用多个阶段来创建 Bean，确保正确的依赖顺序：

```
1. 扫描包路径 → 收集候选 Bean 类名集合
  ↓
2. 创建 BeanDefinition → 解析 @Component/@Configuration/@Bean 等注解
  ↓
3. 创建 @Configuration 类型的 Bean（强依赖，优先创建）
  ↓
4. 创建 BeanPostProcessor 类型的 Bean（用于 Bean 处理）
  ↓
5. 创建普通 Bean（非 @Configuration 工厂 Bean）
  ├─ 解析构造方法或 @Bean 工厂方法的参数（@Value/@Autowired）
  ├─ 递归创建尚未初始化的依赖 Bean
  └─ 调用 BeanPostProcessor.postProcessBeforeInitialization()
  ↓
6. 注入依赖（字段和 setter 方法的 @Value/@Autowired 注解）
  ├─ 在当前类查找 Field 和 Method 并注入
  └─ 在父类查找 Field 和 Method 并注入（支持继承）
  ↓
7. 调用初始化方法（initMethod）
  ↓
8. 调用 BeanPostProcessor.postProcessAfterInitialization()
```

##### 4. BeanPostProcessor 机制

实现 `BeanPostProcessor` 接口可以在 Bean 的生命周期中进行拦截：

- `postProcessBeforeInitialization()` - 初始化前处理
- `postProcessAfterInitialization()` - 初始化后处理
- `postProcessOnSetProperty()` - 用于获取代理原对象

#### 支持的注解

| 注解               | 目标       | 作用                                  |
|------------------|----------|-------------------------------------|
| `@Component`     | 类        | 标注为可管理的 Bean，自动创建实例                 |
| `@Configuration` | 类        | 配置类，其中 `@Bean` 方法作为 Bean 工厂，也是 Bean |
| `@Bean`          | 方法       | 在 `@Configuration` 类中定义 Bean 工厂方法   |
| `@ComponentScan` | 类        | 指定要扫描的包路径，默认当前包                     |
| `@Import`        | 类        | 手动导入其他配置类                           |
| `@Autowired`     | 字段/方法/参数 | 自动装配依赖，可设置 `value=false` 表示可选       |
| `@Value`         | 字段/方法/参数 | 注入属性值，支持表达式和占位符                     |
| `@Primary`       | 类/方法     | 当存在多个候选 Bean 时，作为首选                 |
| `@Order`         | 类/方法     | 控制 Bean 的加载顺序，值越小优先级越高              |
| `@PostConstruct` | 方法       | Bean 初始化后调用的方法                      |
| `@PreDestroy`    | 方法       | Bean 销毁前调用的方法                       |

#### 异常处理

| 异常类                               | 触发场景                         |
|-----------------------------------|------------------------------|
| `BeanCreationException`           | Bean 创建过程中出错                 |
| `BeanDefinitionException`         | Bean 定义有错误（如多个构造方法、命名冲突等）    |
| `BeanNotOfRequiredTypeException`  | 按名称和类型查找时类型不匹配               |
| `InjectionException`              | 注入过程中出错                      |
| `NoSuchBeanDefinitionException`   | 按名称或类型查找 Bean 不存在            |
| `NoUniqueBeanDefinitionException` | 按类型查找时存在多个候选且无 `@Primary` 指定 |
| `ResourceScanException`           | 扫描资源时出现出错                    |
| `UnsatisfiedDependencyException`  | 检测到循环依赖或缺少必要依赖               |

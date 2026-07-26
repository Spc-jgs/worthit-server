package com.shaopc.worthit.common.test.architecture;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * WorthIt 各模块复用的架构依赖规则。
 *
 * <p>消费者应仅在测试作用域引入本类，并由各模块自己的架构测试扫描真实生产代码。</p>
 */
public final class WorthItArchitectureRules {

    /**
     * Common 模块不得依赖任何业务服务或网关包。
     */
    public static final ArchRule COMMON_MUST_NOT_DEPEND_ON_BUSINESS =
            noClasses()
                    .that().resideInAPackage("com.shaopc.worthit.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.shaopc.worthit.auth..",
                            "com.shaopc.worthit.tracking..",
                            "com.shaopc.worthit.reminder..",
                            "com.shaopc.worthit.gateway..")
                    .allowEmptyShould(false)
                    .as("Common 模块不得依赖业务模块");

    /**
     * Client 契约不得反向依赖应用层或实现层。
     */
    public static final ArchRule CLIENT_MUST_NOT_DEPEND_ON_IMPLEMENTATION =
            noClasses()
                    .that().resideInAPackage("..client..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..app..",
                            "..application..",
                            "..domain..",
                            "..infrastructure..")
                    .allowEmptyShould(false)
                    .as("Client 模块不得依赖应用层或实现层");

    /**
     * Domain 层不得依赖接口层、基础设施层、Web 或 MyBatis。
     */
    public static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaces..",
                            "..infrastructure..",
                            "..web..",
                            "org.apache.ibatis..",
                            "com.baomidou.mybatisplus..")
                    .allowEmptyShould(false)
                    .as("Domain 层不得依赖接口层、基础设施层、Web 或 MyBatis");

    /**
     * Gateway 必须保持响应式运行栈，不得引入 Servlet 或 WebMVC。
     */
    public static final ArchRule GATEWAY_MUST_STAY_REACTIVE =
            noClasses()
                    .that().resideInAPackage("..gateway..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.servlet..",
                            "org.springframework.web.servlet..",
                            "org.springframework.web.client..",
                            "org.springframework.jdbc..",
                            "org.flywaydb..",
                            "org.apache.catalina..",
                            "org.apache.tomcat..",
                            "com.shaopc.worthit.common.webmvc..")
                    .allowEmptyShould(false)
                    .as("Gateway 必须保持响应式运行栈");

    /**
     * Servlet 业务应用不得依赖响应式 Web 运行时。
     */
    public static final ArchRule
            SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.shaopc.worthit.auth..",
                            "com.shaopc.worthit.tracking..",
                            "com.shaopc.worthit.reminder..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web.reactive..",
                            "org.springframework.web.server..")
                    .allowEmptyShould(false)
                    .as("Servlet 业务应用不得依赖响应式 Web 运行时");

    /**
     * Client 模块必须只包含契约，不得依赖应用运行时。
     */
    public static final ArchRule CLIENT_MUST_STAY_CONTRACT_ONLY =
            noClasses()
                    .that().resideInAPackage("..client..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.boot..",
                            "org.springdoc..",
                            "jakarta.servlet..",
                            "org.apache.catalina..",
                            "org.apache.tomcat..")
                    .allowEmptyShould(false)
                    .as("Client 模块必须保持纯契约");

    /**
     * Common Web 必须保持运行时中立，不得选择 MVC 或 WebFlux。
     */
    public static final ArchRule COMMON_WEB_MUST_STAY_RUNTIME_NEUTRAL =
            noClasses()
                    .that().resideInAPackage("com.shaopc.worthit.common.web..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web.servlet..",
                            "org.springframework.web.reactive..",
                            "org.springframework.web.server..",
                            "jakarta.servlet..",
                            "org.apache.catalina..",
                            "org.apache.tomcat..",
                            "org.springdoc..")
                    .allowEmptyShould(false)
                    .as("Common Web 必须保持运行时中立");

    private WorthItArchitectureRules() {
    }
}

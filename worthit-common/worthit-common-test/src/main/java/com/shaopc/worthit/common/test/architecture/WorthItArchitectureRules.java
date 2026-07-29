package com.shaopc.worthit.common.test.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * WorthIt 各模块复用的架构依赖规则。
 *
 * <p>消费者应仅在测试作用域引入本类，并由各模块自己的架构测试扫描真实生产代码。</p>
 */
public final class WorthItArchitectureRules {

    private static final ArchCondition<JavaClass>
            IMPLEMENT_MATCHING_SERVICE_INTERFACE =
            new ArchCondition<>(
                    "实现同包同名的 Service 接口") {
                @Override
                public void check(
                        JavaClass implementation,
                        ConditionEvents events) {
                    String simpleName =
                            implementation.getSimpleName();
                    String serviceSimpleName = simpleName.substring(
                            0, simpleName.length() - "Impl".length());
                    String expectedInterface =
                            implementation.getPackageName()
                                    + "." + serviceSimpleName;
                    boolean matches =
                            implementation.getAllRawInterfaces()
                                    .stream()
                                    .anyMatch(contract ->
                                            contract.getName().equals(
                                                    expectedInterface));
                    String message = implementation.getName()
                            + (matches ? " 实现了 " : " 未实现 ")
                            + expectedInterface;
                    events.add(new SimpleConditionEvent(
                            implementation, matches, message));
                }
            };

    /**
     * Application Service 必须用接口声明公开用例。
     */
    public static final ArchRule
            APPLICATION_SERVICES_MUST_BE_INTERFACES =
            classes()
                    .that()
                    .resideInAPackage("..application..")
                    .and()
                    .haveSimpleNameEndingWith("Service")
                    .should()
                    .beInterfaces()
                    .allowEmptyShould(false)
                    .as("Application Service 必须是接口");

    /**
     * Application Service 实现必须采用 ServiceImpl 并实现匹配接口。
     */
    public static final ArchRule
            APPLICATION_SERVICE_IMPLEMENTATIONS_MUST_MATCH_INTERFACES =
            classes()
                    .that()
                    .resideInAPackage("..application..")
                    .and()
                    .haveSimpleNameEndingWith("ServiceImpl")
                    .should(IMPLEMENT_MATCHING_SERVICE_INTERFACE)
                    .allowEmptyShould(false)
                    .as("Application ServiceImpl 必须实现同包同名 Service 接口");

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

    /**
     * WebMVC 自动配置不得反向引入 WebFlux 运行时。
     */
    public static final ArchRule
            WEBMVC_AUTOCONFIGURE_MUST_STAY_SERVLET_ONLY =
            noClasses()
                    .that().resideInAPackage(
                            "com.shaopc.worthit.common.webmvc..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web.reactive..",
                            "org.springframework.web.server..")
                    .allowEmptyShould(false)
                    .as("WebMVC 自动配置必须保持 Servlet 单一运行栈");

    private WorthItArchitectureRules() {
    }
}

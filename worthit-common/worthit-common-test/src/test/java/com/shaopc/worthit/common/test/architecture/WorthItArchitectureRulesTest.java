package com.shaopc.worthit.common.test.architecture;

import com.shaopc.worthit.common.fixture.CommonDependsOnTrackingFixture;
import com.shaopc.worthit.common.fixture.ValidCommonFixture;
import com.shaopc.worthit.common.web.fixture.CommonWebDependsOnWebFluxFixture;
import com.shaopc.worthit.common.web.fixture.ValidCommonWebRuntimeNeutralFixture;
import com.shaopc.worthit.common.webmvc.fixture.ValidWebMvcServletFixture;
import com.shaopc.worthit.common.webmvc.fixture.WebMvcDependsOnWebFluxFixture;
import com.shaopc.worthit.gateway.fixture.GatewayDependsOnRestClientFixture;
import com.shaopc.worthit.gateway.fixture.GatewayDependsOnServletFixture;
import com.shaopc.worthit.gateway.fixture.ValidGatewayFixture;
import com.shaopc.worthit.reminder.app.fixture.ReminderAppFixture;
import com.shaopc.worthit.reminder.client.fixture.ClientDependsOnAppFixture;
import com.shaopc.worthit.reminder.client.fixture.ClientDependsOnBootFixture;
import com.shaopc.worthit.reminder.client.fixture.ValidClientFixture;
import com.shaopc.worthit.tracking.application.fixture.InvalidConcreteService;
import com.shaopc.worthit.tracking.application.fixture.MismatchedServiceImpl;
import com.shaopc.worthit.tracking.application.fixture.ValidExampleService;
import com.shaopc.worthit.tracking.application.fixture.ValidExampleServiceImpl;
import com.shaopc.worthit.tracking.fixture.ServletAppDependsOnWebFluxFixture;
import com.shaopc.worthit.tracking.fixture.TrackingFixture;
import com.shaopc.worthit.tracking.interfaces.fixture.InvalidServiceImplDependencyFixture;
import com.shaopc.worthit.tracking.item.domain.fixture.DomainDependsOnInfrastructureFixture;
import com.shaopc.worthit.tracking.item.domain.fixture.ValidDomainFixture;
import com.shaopc.worthit.tracking.item.infrastructure.fixture.TrackingInfrastructureFixture;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorthItArchitectureRulesTest {

    private final ClassFileImporter importer = new ClassFileImporter();

    @Test
    void applicationServiceRulesAcceptInterfaceAndMatchingImplementation() {
        JavaClasses classes = importer.importClasses(
                ValidExampleService.class,
                ValidExampleServiceImpl.class);

        assertThatCode(() -> {
            WorthItArchitectureRules
                    .APPLICATION_SERVICES_MUST_BE_INTERFACES
                    .check(classes);
            WorthItArchitectureRules
                    .APPLICATION_SERVICE_IMPLEMENTATIONS_MUST_MATCH_INTERFACES
                    .check(classes);
        }).doesNotThrowAnyException();
    }

    @Test
    void applicationServiceRuleRejectsConcreteServiceContract() {
        JavaClasses classes = importer.importClasses(
                InvalidConcreteService.class);

        assertThatThrownBy(() ->
                WorthItArchitectureRules
                        .APPLICATION_SERVICES_MUST_BE_INTERFACES
                        .check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void applicationServiceRuleRejectsMismatchedImplementation() {
        JavaClasses classes = importer.importClasses(
                MismatchedServiceImpl.class);

        assertThatThrownBy(() ->
                WorthItArchitectureRules
                        .APPLICATION_SERVICE_IMPLEMENTATIONS_MUST_MATCH_INTERFACES
                        .check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void interfaceAdapterRuleRejectsServiceImplementationDependency() {
        JavaClasses classes = importer.importClasses(
                InvalidServiceImplDependencyFixture.class,
                ValidExampleServiceImpl.class,
                ValidExampleService.class);

        assertThatThrownBy(() ->
                WorthItArchitectureRules
                        .INTERFACE_ADAPTERS_MUST_NOT_DEPEND_ON_SERVICE_IMPLEMENTATIONS
                        .check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void commonRuleAcceptsIndependentCommonCode() {
        JavaClasses classes = importer.importClasses(ValidCommonFixture.class);

        assertThatCode(() -> WorthItArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_BUSINESS.check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void commonRuleRejectsBusinessDependency() {
        JavaClasses classes = importer.importClasses(
                CommonDependsOnTrackingFixture.class,
                TrackingFixture.class);

        assertThatThrownBy(
                        () -> WorthItArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_BUSINESS
                                .check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void clientRuleAcceptsIndependentClientCode() {
        JavaClasses classes = importer.importClasses(ValidClientFixture.class);

        assertThatCode(
                        () -> WorthItArchitectureRules.CLIENT_MUST_NOT_DEPEND_ON_IMPLEMENTATION
                                .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void clientRuleRejectsApplicationDependency() {
        JavaClasses classes = importer.importClasses(
                ClientDependsOnAppFixture.class,
                ReminderAppFixture.class);

        assertThatThrownBy(
                        () -> WorthItArchitectureRules.CLIENT_MUST_NOT_DEPEND_ON_IMPLEMENTATION
                                .check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void domainRuleAcceptsIndependentDomainCode() {
        JavaClasses classes = importer.importClasses(ValidDomainFixture.class);

        assertThatCode(
                        () -> WorthItArchitectureRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS
                                .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void domainRuleRejectsInfrastructureDependency() {
        JavaClasses classes = importer.importClasses(
                DomainDependsOnInfrastructureFixture.class,
                TrackingInfrastructureFixture.class);

        assertThatThrownBy(
                        () -> WorthItArchitectureRules.DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS
                                .check(classes))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void gatewayRuntimeRuleAcceptsReactiveGatewayCode() {
        JavaClasses classes = importer.importClasses(ValidGatewayFixture.class);

        assertThatCode(
                        () -> WorthItArchitectureRules.GATEWAY_MUST_STAY_REACTIVE
                                .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void gatewayRuntimeRuleRejectsServletDependency() {
        JavaClasses classes = importer.importClasses(
                GatewayDependsOnServletFixture.class);

        assertThatThrownBy(
                        () -> WorthItArchitectureRules.GATEWAY_MUST_STAY_REACTIVE
                                .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jakarta.servlet");
    }

    @Test
    void gatewayRuntimeRuleRejectsBlockingHttpClientDependency() {
        JavaClasses classes = importer.importClasses(
                GatewayDependsOnRestClientFixture.class);

        assertThatThrownBy(
                        () -> WorthItArchitectureRules.GATEWAY_MUST_STAY_REACTIVE
                                .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.springframework.web.client");
    }

    @Test
    void servletAppRuntimeRuleAcceptsServletApplicationCode() {
        JavaClasses classes = importer.importClasses(TrackingFixture.class);

        assertThatCode(() ->
                WorthItArchitectureRules
                        .SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME
                        .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void servletAppRuntimeRuleRejectsWebFluxDependency() {
        JavaClasses classes = importer.importClasses(
                ServletAppDependsOnWebFluxFixture.class);

        assertThatThrownBy(() ->
                WorthItArchitectureRules
                        .SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME
                        .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.springframework.web.reactive");
    }

    @Test
    void clientRuntimeRuleAcceptsContractOnlyClientCode() {
        JavaClasses classes = importer.importClasses(ValidClientFixture.class);

        assertThatCode(
                        () -> WorthItArchitectureRules.CLIENT_MUST_STAY_CONTRACT_ONLY
                                .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void clientRuntimeRuleRejectsBootDependency() {
        JavaClasses classes = importer.importClasses(ClientDependsOnBootFixture.class);

        assertThatThrownBy(
                        () -> WorthItArchitectureRules.CLIENT_MUST_STAY_CONTRACT_ONLY
                                .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.springframework.boot");
    }

    @Test
    void commonWebRuntimeRuleAllowsSwaggerContractAnnotations() {
        JavaClasses classes = importer.importClasses(
                ValidCommonWebRuntimeNeutralFixture.class);

        assertThatCode(
                        () -> WorthItArchitectureRules.COMMON_WEB_MUST_STAY_RUNTIME_NEUTRAL
                                .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void commonWebRuntimeRuleRejectsWebFluxDependency() {
        JavaClasses classes = importer.importClasses(
                CommonWebDependsOnWebFluxFixture.class);

        assertThatThrownBy(
                        () -> WorthItArchitectureRules.COMMON_WEB_MUST_STAY_RUNTIME_NEUTRAL
                                .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.springframework.web.reactive");
    }

    @Test
    void webMvcAutoconfigureRuleAcceptsServletDependency() {
        JavaClasses classes = importer.importClasses(
                ValidWebMvcServletFixture.class);

        assertThatCode(() ->
                WorthItArchitectureRules
                        .WEBMVC_AUTOCONFIGURE_MUST_STAY_SERVLET_ONLY
                        .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void webMvcAutoconfigureRuleRejectsWebFluxDependency() {
        JavaClasses classes = importer.importClasses(
                WebMvcDependsOnWebFluxFixture.class);

        assertThatThrownBy(() ->
                WorthItArchitectureRules
                        .WEBMVC_AUTOCONFIGURE_MUST_STAY_SERVLET_ONLY
                        .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.springframework.web.reactive");
    }
}

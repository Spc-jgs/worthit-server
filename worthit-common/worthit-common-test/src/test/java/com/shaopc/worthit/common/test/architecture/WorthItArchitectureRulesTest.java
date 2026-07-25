package com.shaopc.worthit.common.test.architecture;

import com.shaopc.worthit.common.fixture.CommonDependsOnTrackingFixture;
import com.shaopc.worthit.common.fixture.ValidCommonFixture;
import com.shaopc.worthit.common.web.fixture.CommonWebDependsOnWebFluxFixture;
import com.shaopc.worthit.common.web.fixture.ValidCommonWebRuntimeNeutralFixture;
import com.shaopc.worthit.reminder.app.fixture.ReminderAppFixture;
import com.shaopc.worthit.reminder.client.fixture.ClientDependsOnAppFixture;
import com.shaopc.worthit.reminder.client.fixture.ClientDependsOnBootFixture;
import com.shaopc.worthit.reminder.client.fixture.ValidClientFixture;
import com.shaopc.worthit.gateway.fixture.GatewayDependsOnServletFixture;
import com.shaopc.worthit.gateway.fixture.ValidGatewayFixture;
import com.shaopc.worthit.tracking.fixture.TrackingFixture;
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
}

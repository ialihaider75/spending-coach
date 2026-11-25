package com.intellermatrix.spending_coach.agent.provider;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.provider.config.ProviderConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private static final ChatMessage SYSTEM_MESSAGE = new ChatMessage("system", "be helpful", null, null);
    private static final ChatMessage USER_MESSAGE = new ChatMessage("user", "how much did I spend?", null, null);
    private static final ProviderRequest REQUEST = new ProviderRequest(List.of(SYSTEM_MESSAGE, USER_MESSAGE), List.of());

    private ProviderConfig configFor(String baseUrl) {
        return new ProviderConfig("test", baseUrl, "test-key", "test-model", 10, 10_000, 100);
    }

    // A fresh registry per call keeps each test's breaker state fully isolated -
    // CircuitBreakerRegistry.ofDefaults() uses a minimum-number-of-calls high enough
    // that a single failing call never trips it anyway, but isolation matters once a
    // test deliberately trips one (see the breaker test below).
    private OpenAiCompatibleLlmProvider providerFor(String baseUrl) {
        return providerFor(baseUrl, RestClient.builder(), CircuitBreakerRegistry.ofDefaults());
    }

    private OpenAiCompatibleLlmProvider providerFor(String baseUrl, RestClient.Builder builder) {
        return providerFor(baseUrl, builder, CircuitBreakerRegistry.ofDefaults());
    }

    private OpenAiCompatibleLlmProvider providerFor(String baseUrl, RestClient.Builder builder,
                                                      CircuitBreakerRegistry registry) {
        return new OpenAiCompatibleLlmProvider(configFor(baseUrl), builder, registry);
    }

    @Test
    void parsesAToolCallResponseIntoAChatMessage() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[{"message":{"role":"assistant","tool_calls":[
              {"id":"call-1","function":{"name":"get_overview","arguments":"{}"}}]}}],
             "usage":{"prompt_tokens":120,"completion_tokens":15}}""")));
        var provider = providerFor(wireMock.baseUrl());

        var response = provider.chat(REQUEST);

        assertThat(response.message().toolCalls()).hasSize(1);
        assertThat(response.message().toolCalls().get(0).toolName()).isEqualTo("get_overview");
        assertThat(response.message().toolCalls().get(0).id()).isEqualTo("call-1");
        assertThat(response.message().toolCalls().get(0).arguments()).isEmpty();
        assertThat(response.promptTokens()).isEqualTo(120);
        assertThat(response.completionTokens()).isEqualTo(15);
    }

    @Test
    void parsesAPlainTextResponseWithNoToolCalls() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[{"message":{"role":"assistant","content":"You spent 1,200 this month."}}],
             "usage":{"prompt_tokens":80,"completion_tokens":10}}""")));
        var provider = providerFor(wireMock.baseUrl());

        var response = provider.chat(REQUEST);

        assertThat(response.message().content()).isEqualTo("You spent 1,200 this month.");
        assertThat(response.message().toolCalls()).isNull();
    }

    @Test
    void malformedToolCallArgumentsProduceAStructuredErrorInsteadOfThrowing() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[{"message":{"role":"assistant","tool_calls":[
              {"id":"call-2","function":{"name":"simulate_savings","arguments":"{\\"adjustments\\": [incomplete"}}]}}],
             "usage":{"prompt_tokens":200,"completion_tokens":20}}""")));
        var provider = providerFor(wireMock.baseUrl());

        var response = provider.chat(REQUEST);

        var toolCall = response.message().toolCalls().get(0);
        // The real tool name is NOT reused as-is: DefaultToolRegistry looks tools up by
        // an exact name() match, so a mangled name routes this through its existing
        // "Unknown tool" error path instead of (wrongly) dispatching to the real tool
        // with garbage/missing arguments.
        assertThat(toolCall.toolName()).startsWith("simulate_savings").isNotEqualTo("simulate_savings");
        assertThat(toolCall.arguments()).containsKey("parseError");
    }

    @Test
    void malformedAskUserArgumentsDoNotMatchTheRealAskUserToolName() {
        // DefaultAgentLoopService special-cases ask_user by an exact toolName match,
        // BEFORE ToolArgumentValidator ever runs - bypassing the normal
        // missing-required-field error path entirely. A mangled name on parse failure
        // is what keeps a malformed ask_user call from silently pausing the run with a
        // null question instead of reporting an error (see commit history for the
        // review finding this closes).
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[{"message":{"role":"assistant","tool_calls":[
              {"id":"call-3","function":{"name":"ask_user","arguments":"{\\"question\\": incomplete"}}]}}],
             "usage":{"prompt_tokens":90,"completion_tokens":5}}""")));
        var provider = providerFor(wireMock.baseUrl());

        var response = provider.chat(REQUEST);

        assertThat(response.message().toolCalls().get(0).toolName()).isNotEqualTo("ask_user");
    }

    @Test
    void emptyChoicesArrayDoesNotThrow() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[], "usage":{"prompt_tokens":10,"completion_tokens":0}}""")));
        var provider = providerFor(wireMock.baseUrl());

        var response = provider.chat(REQUEST);

        assertThat(response.message().content()).isNull();
        assertThat(response.message().toolCalls()).isNull();
    }

    @Test
    void aTooManyRequestsResponseThrowsProviderUnavailable() {
        wireMock.stubFor(post("/chat/completions").willReturn(aResponse().withStatus(429)));
        var provider = providerFor(wireMock.baseUrl());

        assertThatThrownBy(() -> provider.chat(REQUEST)).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void aServerErrorResponseThrowsProviderUnavailable() {
        wireMock.stubFor(post("/chat/completions").willReturn(aResponse().withStatus(503)));
        var provider = providerFor(wireMock.baseUrl());

        assertThatThrownBy(() -> provider.chat(REQUEST)).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void aResponseDelayedPastTheConfiguredTimeoutThrowsProviderUnavailable() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("{}").withFixedDelay(500)));
        var shortTimeoutFactory = new SimpleClientHttpRequestFactory();
        shortTimeoutFactory.setReadTimeout(Duration.ofMillis(150));
        var provider = providerFor(wireMock.baseUrl(), RestClient.builder().requestFactory(shortTimeoutFactory));

        assertThatThrownBy(() -> provider.chat(REQUEST)).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void aNonTooManyRequestsClientErrorRethrowsUnchanged() {
        wireMock.stubFor(post("/chat/completions").willReturn(aResponse().withStatus(400)));
        var provider = providerFor(wireMock.baseUrl());

        assertThatThrownBy(() -> provider.chat(REQUEST))
            .isInstanceOf(RestClientResponseException.class)
            .isNotInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void threeConsecutiveFailuresOpenTheBreakerBeforeAFourthRealCallIsEverMade() {
        String scenario = "circuit-breaker";
        wireMock.stubFor(post("/chat/completions").inScenario(scenario)
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("failed-once")
            .willReturn(aResponse().withStatus(503)));
        wireMock.stubFor(post("/chat/completions").inScenario(scenario)
            .whenScenarioStateIs("failed-once")
            .willSetStateTo("failed-twice")
            .willReturn(aResponse().withStatus(503)));
        wireMock.stubFor(post("/chat/completions").inScenario(scenario)
            .whenScenarioStateIs("failed-twice")
            .willSetStateTo("breaker-should-be-open")
            .willReturn(aResponse().withStatus(503)));
        // This stub would succeed if ever called - proving a would-be 4th call never
        // actually reaches WireMock is what shows the breaker, not luck, stopped it.
        wireMock.stubFor(post("/chat/completions").inScenario(scenario)
            .whenScenarioStateIs("breaker-should-be-open")
            .willReturn(okJson("""
                {"choices":[{"message":{"role":"assistant","content":"should never be seen"}}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1}}""")));

        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(3)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(100)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build();
        var provider = providerFor(wireMock.baseUrl(), RestClient.builder(), CircuitBreakerRegistry.of(breakerConfig));

        for (int call = 1; call <= 3; call++) {
            assertThatThrownBy(() -> provider.chat(REQUEST)).isInstanceOf(ProviderUnavailableException.class);
        }
        // The breaker is open now - a 4th attempt must be rejected before any network call.
        assertThatThrownBy(() -> provider.chat(REQUEST)).isInstanceOf(ProviderUnavailableException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/chat/completions")));
    }
}

package com.intellermatrix.spending_coach.agent.provider;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.intellermatrix.spending_coach.agent.dto.provider.ChatMessage;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.provider.config.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmProviderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private static final ChatMessage SYSTEM_MESSAGE = new ChatMessage("system", "be helpful", null, null);
    private static final ChatMessage USER_MESSAGE = new ChatMessage("user", "how much did I spend?", null, null);

    private ProviderConfig configFor(String baseUrl) {
        return new ProviderConfig("test", baseUrl, "test-key", "test-model", 10, 10_000, 100);
    }

    @Test
    void parsesAToolCallResponseIntoAChatMessage() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[{"message":{"role":"assistant","tool_calls":[
              {"id":"call-1","function":{"name":"get_overview","arguments":"{}"}}]}}],
             "usage":{"prompt_tokens":120,"completion_tokens":15}}""")));
        var provider = new OpenAiCompatibleLlmProvider(configFor(wireMock.baseUrl()), RestClient.builder());

        var response = provider.chat(new ProviderRequest(List.of(SYSTEM_MESSAGE, USER_MESSAGE), List.of()));

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
        var provider = new OpenAiCompatibleLlmProvider(configFor(wireMock.baseUrl()), RestClient.builder());

        var response = provider.chat(new ProviderRequest(List.of(SYSTEM_MESSAGE, USER_MESSAGE), List.of()));

        assertThat(response.message().content()).isEqualTo("You spent 1,200 this month.");
        assertThat(response.message().toolCalls()).isNull();
    }

    @Test
    void malformedToolCallArgumentsProduceAStructuredErrorInsteadOfThrowing() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[{"message":{"role":"assistant","tool_calls":[
              {"id":"call-2","function":{"name":"simulate_savings","arguments":"{\\"adjustments\\": [incomplete"}}]}}],
             "usage":{"prompt_tokens":200,"completion_tokens":20}}""")));
        var provider = new OpenAiCompatibleLlmProvider(configFor(wireMock.baseUrl()), RestClient.builder());

        var response = provider.chat(new ProviderRequest(List.of(SYSTEM_MESSAGE, USER_MESSAGE), List.of()));

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
        var provider = new OpenAiCompatibleLlmProvider(configFor(wireMock.baseUrl()), RestClient.builder());

        var response = provider.chat(new ProviderRequest(List.of(SYSTEM_MESSAGE, USER_MESSAGE), List.of()));

        assertThat(response.message().toolCalls().get(0).toolName()).isNotEqualTo("ask_user");
    }

    @Test
    void emptyChoicesArrayDoesNotThrow() {
        wireMock.stubFor(post("/chat/completions").willReturn(okJson("""
            {"choices":[], "usage":{"prompt_tokens":10,"completion_tokens":0}}""")));
        var provider = new OpenAiCompatibleLlmProvider(configFor(wireMock.baseUrl()), RestClient.builder());

        var response = provider.chat(new ProviderRequest(List.of(SYSTEM_MESSAGE, USER_MESSAGE), List.of()));

        assertThat(response.message().content()).isNull();
        assertThat(response.message().toolCalls()).isNull();
    }
}

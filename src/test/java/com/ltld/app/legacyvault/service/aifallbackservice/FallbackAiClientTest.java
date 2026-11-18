package com.ltld.app.legacyvault.service.aifallbackservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import com.ltld.app.legacyvault.enums.AiFallbackProvider;
import com.ltld.app.legacyvault.service.aisettingsservice.AiFeatureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FallbackAiClientTest {

    private static final String GROQ = "https://api.groq.com/openai/v1";
    private static final String DEEPSEEK = "https://api.deepseek.com";
    private static final String REPLY = """
            {"choices":[{"message":{"role":"assistant","content":"Xin chào từ AI dự phòng"}}]}
            """;

    private MockRestServiceServer server;
    private FallbackAiClient client;

    private FallbackAiClient build(String groqKey, String deepseekKey, String defaultText) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new FallbackAiClient(groqKey, GROQ + "/", "llama-3.3-70b-versatile",
                "meta-llama/llama-4-scout-17b-16e-instruct", "qwen-vision, ,", deepseekKey, DEEPSEEK,
                "deepseek-chat", "deepseek-reasoner", defaultText, builder);
    }

    @BeforeEach
    void setUp() {
        client = build("groq-key", "ds-key", "deepseek");
    }

    @Test
    void chat_defaultProvider_isDeepSeek_withBearerKey_andSystemPromptFirst() {
        server.expect(requestTo(DEEPSEEK + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ds-key"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Bạn là Vaulty"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        String reply = client.chat(null, "Bạn là Vaulty",
                List.of(new ChatMessage("user", "Chào"), new ChatMessage("assistant", "Chào bạn")), 0.4, 1024);

        assertThat(reply).isEqualTo("Xin chào từ AI dự phòng");
        server.verify();
    }

    @Test
    void chat_adminChoseGroq_andAllowedModel_usesThem() {
        AiFeatureConfig cfg = new AiFeatureConfig(true, null, null, true, AiFallbackProvider.GROQ, "qwen-vision", null);
        server.expect(requestTo(GROQ + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer groq-key"))
                .andExpect(jsonPath("$.model").value("qwen-vision"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        client.chat(cfg, "sys", List.of(new ChatMessage("user", "hi")), 0.4, 100);
        server.verify();
    }

    @Test
    void chat_modelNotAllowedForProvider_fallsBackToProviderDefault() {
        // Model DeepSeek không thuộc danh sách Groq -> dùng model văn bản mặc định của Groq
        AiFeatureConfig cfg = new AiFeatureConfig(true, null, null, true, AiFallbackProvider.GROQ, "deepseek-chat", null);
        server.expect(requestTo(GROQ + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("llama-3.3-70b-versatile"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        client.chat(cfg, "sys", List.of(new ChatMessage("user", "hi")), 0.4, 100);
        server.verify();
    }

    @Test
    void visionJson_alwaysGroq_sendsJpegDataUrl_andJsonMode() {
        server.expect(requestTo(GROQ + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer groq-key"))
                .andExpect(jsonPath("$.model").value("meta-llama/llama-4-scout-17b-16e-instruct"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[0].content[1].image_url.url").value(
                        org.hamcrest.Matchers.startsWith("data:image/jpeg;base64,")))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"citizenId\\":\\"001\\"}"}}]}
                        """, MediaType.APPLICATION_JSON));

        String raw = client.visionJson(null, "Đọc CCCD", List.of(new byte[]{1, 2, 3}), 512);

        assertThat(raw).isEqualTo("{\"citizenId\":\"001\"}");
        server.verify();
    }

    @Test
    void complete_httpError_propagates() {
        server.expect(requestTo(DEEPSEEK + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.textJson(null, "sys", "text", 100))
                .isInstanceOf(AiProviderException.class)
                .hasCauseInstanceOf(HttpServerErrorException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void availability_followsKeys_andFeatureToggle() {
        FallbackAiClient onlyGroq = build("groq-key", "", "deepseek");
        assertThat(onlyGroq.textAvailable(null)).isFalse();      // mặc định DeepSeek nhưng chưa có key
        assertThat(onlyGroq.visionAvailable(null)).isTrue();
        AiFeatureConfig groqText = new AiFeatureConfig(true, null, null, true, AiFallbackProvider.GROQ, null, null);
        assertThat(onlyGroq.textAvailable(groqText)).isTrue();

        AiFeatureConfig off = new AiFeatureConfig(true, null, null, false, null, null, null);
        assertThat(client.textAvailable(off)).isFalse();
        assertThat(client.visionAvailable(off)).isFalse();
    }

    @Test
    void allowedModels_includeDefaultsAndExtras_withoutBlanks() {
        assertThat(client.allowedModels(AiFallbackProvider.GROQ)).containsExactly(
                "llama-3.3-70b-versatile", "meta-llama/llama-4-scout-17b-16e-instruct", "qwen-vision");
        assertThat(client.allowedModels(AiFallbackProvider.DEEPSEEK)).containsExactly("deepseek-chat", "deepseek-reasoner");
        assertThat(build("k", "k", "nonsense").defaultTextProvider()).isEqualTo(AiFallbackProvider.DEEPSEEK);
        assertThat(build("k", "k", "GROQ").defaultTextProvider()).isEqualTo(AiFallbackProvider.GROQ);
    }

    @Test
    void jsonObject_stripsFencesAndProse() {
        assertThat(FallbackAiClient.jsonObject("Here you go:\n```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(FallbackAiClient.jsonObject(null)).isEqualTo("{}");
    }

    @Test
    void modelNotFound_isReportedWithProviderMessage_noContent() {
        server.expect(requestTo(GROQ + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"message":"The model `meta-llama/llama-4-scout-17b-16e-instruct` does not exist or you do not have access to it.","type":"invalid_request_error","code":"model_not_found"}}
                        """));

        assertThatThrownBy(() -> client.visionJson(null, "Đọc giấy tờ bí mật", List.of(new byte[]{1}), 100))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("GROQ model 'meta-llama/llama-4-scout-17b-16e-instruct' HTTP 404")
                .hasMessageContaining("does not exist")
                .hasMessageNotContaining("Đọc giấy tờ bí mật");
    }

    @Test
    void responseWithOctetStreamContentType_isStillParsed() {
        server.expect(requestTo(DEEPSEEK + "/chat/completions"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.textJson(null, "sys", "text", 100)).isEqualTo("Xin chào từ AI dự phòng");
    }

    @Test
    void providerError_handlesEmptyOrOddBodies() {
        assertThat(FallbackAiClient.providerError(null)).isEqualTo("(no message)");
        assertThat(FallbackAiClient.providerError("<html>502</html>")).isEqualTo("(unreadable error body)");
    }
}

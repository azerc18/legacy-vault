package com.ltld.app.legacyvault.service.assistantservice;

import com.ltld.app.legacyvault.dto.assistantdto.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiAiClientTest {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final String PRIMARY_URL = BASE + "/models/gemini-3.6-flash:generateContent";
    private static final String BACKUP_URL = BASE + "/models/gemini-3.5-flash:generateContent";

    private MockRestServiceServer server;
    private GeminiAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiAiClient(
                "primary-key",
                "backup-key",
                "gemini-3.6-flash",
                "gemini-3.5-flash",
                builder
        );
    }

    @Test
    void chat_returnsPrimaryResponse_whenPrimarySucceeds() {
        server.expect(requestTo(PRIMARY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "primary-key"))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {"text": "Xin chào! Tôi là trợ lý Vaulty."}
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String reply = client.chat("Bạn là trợ lý Vaulty", List.of(new ChatMessage("user", "Chào bạn")));

        assertThat(reply).isEqualTo("Xin chào! Tôi là trợ lý Vaulty.");
        server.verify();
    }

    @Test
    void chat_fallsBackToBackupModel_whenPrimaryFailsWith503ServiceUnavailable() {
        server.expect(requestTo(PRIMARY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "primary-key"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        server.expect(requestTo(BACKUP_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "backup-key"))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {"text": "Phản hồi dự phòng từ Vaulty."}
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String reply = client.chat("Bạn là trợ lý Vaulty", List.of(new ChatMessage("user", "Chào bạn")));

        assertThat(reply).isEqualTo("Phản hồi dự phòng từ Vaulty.");
        server.verify();
    }

    @Test
    void chat_fallsBackToBackupModel_whenPrimaryFailsWith429TooManyRequests() {
        server.expect(requestTo(PRIMARY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "primary-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        server.expect(requestTo(BACKUP_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "backup-key"))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {"text": "Phản hồi sau khi model chính hết quota."}
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String reply = client.chat("Bạn là trợ lý Vaulty", List.of(new ChatMessage("user", "Chào bạn")));

        assertThat(reply).isEqualTo("Phản hồi sau khi model chính hết quota.");
        server.verify();
    }

    @Test
    void chat_doesNotFallBack_whenPrimaryFailsWith400BadRequest() {
        server.expect(requestTo(PRIMARY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "primary-key"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.chat("System", List.of(new ChatMessage("user", "Bad request"))))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        // Verify that server did not receive any backup request
        server.verify();
    }

    @Test
    void chat_fallsBackToBackupModel_whenPrimaryModelIsNotFound() {
        // Google trả 404 khi model chính bị ngừng hoặc đổi tên: model dự phòng vẫn trả lời được
        server.expect(requestTo(PRIMARY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "primary-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        server.expect(requestTo(BACKUP_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "backup-key"))
                .andRespond(withSuccess("""
                        {"candidates": [{"content": {"parts": [{"text": "Model dự phòng trả lời."}]}}]}
                        """, MediaType.APPLICATION_JSON));

        String reply = client.chat("Bạn là trợ lý Vaulty", List.of(new ChatMessage("user", "Chào bạn")));

        assertThat(reply).isEqualTo("Model dự phòng trả lời.");
        server.verify();
    }

    @Test
    void chat_throwsException_whenBothPrimaryAndBackupFail() {
        server.expect(requestTo(PRIMARY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "primary-key"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        server.expect(requestTo(BACKUP_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "backup-key"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.chat("System", List.of(new ChatMessage("user", "Test"))))
                .isInstanceOf(HttpServerErrorException.class);

        server.verify();
    }
}

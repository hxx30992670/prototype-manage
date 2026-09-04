package com.company.prototype.gateway;

import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.common.ticket.ContentTicketRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PreviewGatewayApplication.class)
@ActiveProfiles("test")
class ContentGatewayIT {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ObjectStorage objectStorage;

    @Autowired
    private ObjectMapper objectMapper;

    private final String rawTicket = "ticket1234567890abcdef";
    private String ticketDigest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        ticketDigest = ContentTicketVerifier.hashTicket(rawTicket);
    }

    @Test
    void ticketAllowsAssetServingWithSecurityHeaders() throws Exception {
        ContentTicketRecord record = new ContentTicketRecord(
            ticketDigest,
            "INTERNAL",
            "user1",
            100L,
            "prototypes/1/versions/1/extracted/",
            "index.html",
            Instant.now().toEpochMilli(),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        when(redisTemplate.opsForValue()).thenReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redisTemplate.opsForValue().get("ticket:content:" + ticketDigest))
            .thenReturn(objectMapper.writeValueAsString(record));

        String assetKey = "prototypes/1/versions/1/extracted/index.html";
        when(objectStorage.stat(eq(assetKey)))
            .thenReturn(new ObjectStorage.ObjectMetadata(assetKey, 23L, "hash", "text/html"));
        when(objectStorage.open(eq(assetKey)))
            .thenReturn(new ByteArrayInputStream("<h1>Hello Prototype</h1>".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/content/c/" + rawTicket + "/index.html"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "*"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("Content-Security-Policy"))
            .andExpect(content().string("<h1>Hello Prototype</h1>"));
    }

    @Test
    void pathTraversalEscapingIsRejectedWith400() throws Exception {
        ContentTicketRecord record = new ContentTicketRecord(
            ticketDigest,
            "INTERNAL",
            "user1",
            100L,
            "prototypes/1/versions/1/extracted/",
            "index.html",
            Instant.now().toEpochMilli(),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        when(redisTemplate.opsForValue()).thenReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redisTemplate.opsForValue().get("ticket:content:" + ticketDigest))
            .thenReturn(objectMapper.writeValueAsString(record));

        mockMvc.perform(get("/content/c/" + rawTicket + "/../escaping.txt"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void expiredOrMissingTicketIsRejectedWith403() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redisTemplate.opsForValue().get("ticket:content:" + ticketDigest))
            .thenReturn(null);

        mockMvc.perform(get("/content/c/" + rawTicket + "/index.html"))
            .andExpect(status().isForbidden());
    }

    @Test
    void missingAssetReturns404() throws Exception {
        ContentTicketRecord record = new ContentTicketRecord(
            ticketDigest,
            "INTERNAL",
            "user1",
            100L,
            "prototypes/1/versions/1/extracted/",
            "index.html",
            Instant.now().toEpochMilli(),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        when(redisTemplate.opsForValue()).thenReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redisTemplate.opsForValue().get("ticket:content:" + ticketDigest))
            .thenReturn(objectMapper.writeValueAsString(record));

        when(objectStorage.stat(eq("prototypes/1/versions/1/extracted/missing.png")))
            .thenReturn(null);

        mockMvc.perform(get("/content/c/" + rawTicket + "/missing.png"))
            .andExpect(status().isNotFound());
    }

    @Test
    void headRequestReturnsHeadersWithoutBody() throws Exception {
        ContentTicketRecord record = new ContentTicketRecord(
            ticketDigest,
            "INTERNAL",
            "user1",
            100L,
            "prototypes/1/versions/1/extracted/",
            "index.html",
            Instant.now().toEpochMilli(),
            Instant.now().plus(1, ChronoUnit.HOURS)
        );

        when(redisTemplate.opsForValue()).thenReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redisTemplate.opsForValue().get("ticket:content:" + ticketDigest))
            .thenReturn(objectMapper.writeValueAsString(record));

        String assetKey = "prototypes/1/versions/1/extracted/index.html";
        when(objectStorage.stat(eq(assetKey)))
            .thenReturn(new ObjectStorage.ObjectMetadata(assetKey, 23L, "hash", "text/html"));

        mockMvc.perform(head("/content/c/" + rawTicket + "/index.html"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "*"))
            .andExpect(content().string(""));
    }

    @Test
    void shareTicketUsesShareNamespaceAndValidSessionEpoch() throws Exception {
        ContentTicketRecord record = new ContentTicketRecord(
            ticketDigest,
            "SHARE",
            "session-1",
            101L,
            "prototypes/1/versions/1/extracted/",
            "index.html",
            3L,
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("content:ticket:" + ticketDigest))
            .thenReturn(objectMapper.writeValueAsString(record));
        when(values.get("share:session:session-1")).thenReturn("""
            {"shareLinkId":7,"authEpoch":3,"csrfTokenDigest":"digest","guestName":"访客",
            "expiresAt":"%s"}
            """.formatted(Instant.now().plus(1, ChronoUnit.HOURS)));
        when(values.get("share:auth-epoch:7")).thenReturn("3");

        String assetKey = "prototypes/1/versions/1/extracted/index.html";
        when(objectStorage.stat(eq(assetKey)))
            .thenReturn(new ObjectStorage.ObjectMetadata(assetKey, 23L, "hash", "text/html"));
        when(objectStorage.open(eq(assetKey)))
            .thenReturn(new ByteArrayInputStream("<h1>Shared Prototype</h1>".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/content/c/" + rawTicket + "/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string("<h1>Shared Prototype</h1>"));
    }

    @Test
    void shareTicketWithStaleAuthEpochIsRejected() throws Exception {
        ContentTicketRecord record = new ContentTicketRecord(
            ticketDigest,
            "SHARE",
            "session-1",
            101L,
            "prototypes/1/versions/1/extracted/",
            "index.html",
            3L,
            Instant.now().plus(1, ChronoUnit.HOURS)
        );
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("content:ticket:" + ticketDigest))
            .thenReturn(objectMapper.writeValueAsString(record));
        when(values.get("share:session:session-1")).thenReturn("""
            {"shareLinkId":7,"authEpoch":3,"csrfTokenDigest":"digest","guestName":"访客",
            "expiresAt":"%s"}
            """.formatted(Instant.now().plus(1, ChronoUnit.HOURS)));
        when(values.get("share:auth-epoch:7")).thenReturn("4");

        mockMvc.perform(get("/content/c/" + rawTicket + "/index.html"))
            .andExpect(status().isForbidden());
    }
}

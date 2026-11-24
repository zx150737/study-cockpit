package com.study.cockpit.security;

import com.study.cockpit.config.AppProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationRateLimitTest {
    @Test
    void registrationUsesCredentialLimitAndRejectsExcessRequests() throws Exception {
        AppProperties props = mock(AppProperties.class);
        when(props.getLoginRateLimitPerMinute()).thenReturn(5L);
        SecurityCacheService cache = mock(SecurityCacheService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RateLimitFilter(cache, props).doFilter(request, response, chain);
        verify(cache).tryAcquire("192.0.2.1|/api/auth/register", 5L);
        assertEquals(429, response.getStatus());
        verifyNoInteractions(chain);
    }
}

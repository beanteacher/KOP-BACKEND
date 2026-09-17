package com.kop.finance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kop.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security 기본 동작은 인증 자체가 없을 때도 403을 준다(익명 인증을 "인증됨"으로 취급).
 * 06-api-design.md는 "토큰 없음/만료"를 401로 규정하므로 여기서 맞춰준다. 역할 부족(인가 실패)은
 * {@link RestAccessDeniedHandler}가 403으로 그대로 낸다.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
        throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
            ErrorResponse.of("UNAUTHORIZED", "토큰이 없거나 만료되었습니다")
        ));
    }
}

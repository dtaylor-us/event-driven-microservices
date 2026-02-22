package demo.grid.ingest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Puts X-Correlation-Id (or request URI as fallback) into MDC so logs include correlationId.
 * Clears MDC after the request.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getRequestId() != null ? request.getRequestId() : "ingest-" + System.currentTimeMillis();
        }
        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}

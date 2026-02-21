package demo.grid.ingest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests that do not carry a valid X-API-Key header.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey != null ? expectedApiKey : "";
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER_API_KEY);
        if (provided == null || !provided.equals(expectedApiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid X-API-Key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

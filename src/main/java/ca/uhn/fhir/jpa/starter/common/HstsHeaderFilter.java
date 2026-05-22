package ca.uhn.fhir.jpa.starter.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that adds HTTP Strict Transport Security (HSTS) header to all responses.
 * 
 * HSTS instructs browsers to only access the application over HTTPS, preventing
 * protocol downgrade attacks and cookie hijacking.
 * 
 * Security Benefits:
 * - Prevents man-in-the-middle attacks
 * - Protects against protocol downgrade attacks
 * - Ensures all communication is encrypted
 * 
 * Configuration:
 * - max-age=31536000: HSTS policy valid for 1 year (recommended by OWASP)
 * - includeSubDomains: Apply policy to all subdomains
 * 
 * Compliance:
 * - OWASP Top 10 2021: A7-Identification and Authentication Failures
 * - OWASP Top 10 2025: A07-Authentication Failures
 * - OWASP ASVS: V14 Configuration
 * - PCI DSS v4.0: 6.2.4 Vulnerabilities in software development
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HstsHeaderFilter implements Filter {
	
	private static final Logger logger = LoggerFactory.getLogger(HstsHeaderFilter.class);
	
	/**
	 * HSTS header value:
	 * - max-age=31536000: 1 year in seconds (OWASP recommended minimum)
	 * - includeSubDomains: Apply to all subdomains
	 */
	private static final String HSTS_HEADER_VALUE = "max-age=31536000; includeSubDomains";
	
	private static final String HSTS_HEADER_NAME = "Strict-Transport-Security";
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		logger.info("HSTS Header Filter initialized - HSTS policy will be applied to all responses");
	}
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		if (response instanceof HttpServletResponse) {
			HttpServletResponse httpResponse = (HttpServletResponse) response;
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			
			// Only add HSTS header for HTTPS requests
			// Adding HSTS to HTTP responses can cause issues
			if (isSecureRequest(httpRequest)) {
				httpResponse.setHeader(HSTS_HEADER_NAME, HSTS_HEADER_VALUE);
				
				if (logger.isDebugEnabled()) {
					logger.debug("Added HSTS header to response for: {}", httpRequest.getRequestURI());
				}
			}
		}
		
		chain.doFilter(request, response);
	}
	
	@Override
	public void destroy() {
		logger.info("HSTS Header Filter destroyed");
	}
	
	/**
	 * Checks if the request is secure (HTTPS).
	 * Also checks X-Forwarded-Proto header for proxy/load balancer scenarios.
	 * 
	 * @param request the HTTP request
	 * @return true if the request is secure, false otherwise
	 */
	private boolean isSecureRequest(HttpServletRequest request) {
		// Check if request is directly HTTPS
		if (request.isSecure()) {
			return true;
		}
		
		// Check X-Forwarded-Proto header (common in proxy/load balancer setups)
		String forwardedProto = request.getHeader("X-Forwarded-Proto");
		if (forwardedProto != null && forwardedProto.equalsIgnoreCase("https")) {
			return true;
		}
		
		// Check X-Forwarded-SSL header (alternative header used by some proxies)
		String forwardedSsl = request.getHeader("X-Forwarded-SSL");
		if (forwardedSsl != null && forwardedSsl.equalsIgnoreCase("on")) {
			return true;
		}
		
		return false;
	}
}
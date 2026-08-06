package com.portiq.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Header value longer than this is not a token anyone issued; parsing it just burns CPU. */
    private static final int MAX_TOKEN_LENGTH = 4096;

    private final JwtService jwtService;
    private final TokenDenylist tokenDenylist;
    private final SecurityAuditLogger audit;

    public JwtAuthFilter(JwtService jwtService, TokenDenylist tokenDenylist, SecurityAuditLogger audit) {
        this.jwtService = jwtService;
        this.tokenDenylist = tokenDenylist;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();

            if (token.length() > MAX_TOKEN_LENGTH) {
                audit.invalidToken(request, "oversized");
            } else {
                Optional<Claims> claims = jwtService.parse(token);
                if (claims.isEmpty()) {
                    // Expired or tampered-with. Left unauthenticated rather than rejected outright,
                    // so the entry point produces the same 401 as presenting no token at all.
                    audit.invalidToken(request, "signature, issuer or expiry check failed");
                } else if (tokenDenylist.isRevoked(claims.get().getId())) {
                    audit.invalidToken(request, "revoked by logout");
                } else {
                    authenticate(request, claims.get().getSubject());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String username) {
        if (username == null || username.isBlank()) {
            audit.invalidToken(request, "no subject claim");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

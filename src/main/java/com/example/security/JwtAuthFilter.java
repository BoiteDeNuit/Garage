package com.example.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService service;
    private final UserDetailsService userDetailsService;
    private final SecurityErrorWriter errorWriter;
    public JwtAuthFilter(JwtService service,UserDetailsService userDetailsService,SecurityErrorWriter errorWriter)
    {
        this.service=service;
        this.userDetailsService=userDetailsService;
        this.errorWriter=errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
       String header = request.getHeader("Authorization");
       if(header == null || !header.startsWith("Bearer "))
       {
           filterChain.doFilter(request,response);
           return;
       }
       String token = header.substring(7);
       UserDetails user;
       try {
           String username = service.extractUsername(token);
           user = userDetailsService.loadUserByUsername(username);
       }
       catch (JwtException | IllegalArgumentException | UsernameNotFoundException e)
       {
           errorWriter.writeError(request,response, HttpStatus.UNAUTHORIZED,"Недействительный токен");
           return;
       }
        var auth = new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
       filterChain.doFilter(request,response);
    }
}

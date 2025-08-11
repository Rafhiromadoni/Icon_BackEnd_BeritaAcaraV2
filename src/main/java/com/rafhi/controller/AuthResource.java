package com.rafhi.controller;

import com.rafhi.entity.AppUser;
import com.rafhi.dto.LoginRequest;
import com.rafhi.dto.RegisterRequest;


import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.Authenticated;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import io.quarkus.security.identity.SecurityIdentity;
// import java.util.HashSet;
// import java.util.List;

@Path("/auth")
public class AuthResource {

    @Inject
    SecurityIdentity securityIdentity;

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response register(RegisterRequest data) {
        // Cek apakah username sudah ada
        if (AppUser.find("username", data.username).count() > 0) {
            return Response.status(Response.Status.CONFLICT)
                        .entity("{\"message\":\"Username sudah ada.\"}")
                        .build();
        }
        
        // Tambahkan user baru
        AppUser.add(data.username, data.password);
        
        return Response.ok("{\"message\":\"Registrasi berhasil.\"}").build();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response login(LoginRequest credentials) {
        AppUser user = AppUser.find("username", credentials.username).firstResult();
        if (user != null && BcryptUtil.matches(credentials.password, user.password)) {
            String token = Jwt.issuer("https://yourdomain.com/issuer")
                              .upn(user.username)
                            //   .groups(new HashSet<>(List.of(user.role)))
                              .expiresIn(Duration.ofHours(24))
                              .sign();
            return Response.ok(token).build();
        }
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    @GET
    @Path("/me")
    @Authenticated // Endpoint ini hanya bisa diakses oleh user yang sudah login
    @Produces(MediaType.APPLICATION_JSON)
    public Response me() {
        String username = securityIdentity.getPrincipal().getName();
        // Buat objek JSON sederhana untuk dikirim ke frontend
        return Response.ok("{\"username\":\"" + username + "\"}").build();
    }
}
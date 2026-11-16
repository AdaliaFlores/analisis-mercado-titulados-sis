package com.umss.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

public class AuthController {

    public static void registrarRutas(Javalin app) {
        app.post("/api/login", ctx -> {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String user = body.get("usuario");
            String pass = body.get("password");
            if (user == null || pass == null || user.length() > 30 || pass.length() > 30) {
                ctx.status(400).json(Map.of("error", "El usuario y la contraseña no deben superar los 30 caracteres."));
                return;
            }
            String adminUser = System.getenv().getOrDefault("ADMIN_USER", "admin");
            String adminPass = System.getenv().getOrDefault("ADMIN_PASS", "admin123");

            if (adminUser.equals(user) && adminPass.equals(pass)) {
                ctx.sessionAttribute("admin_autenticado", true);
                ctx.status(200).json(Map.of("mensaje", "Login exitoso"));
            } else {
                ctx.status(401).json(Map.of("error", "Usuario o contraseña incorrectos."));
            }
        });

        app.post("/api/logout", ctx -> {
            ctx.req().getSession().invalidate();
            ctx.status(200).json(Map.of("mensaje", "Sesión cerrada"));
        });
    }

    public static boolean estaAutenticado(Context ctx) {
        Boolean auth = ctx.sessionAttribute("admin_autenticado");
        return auth != null && auth;
    }
}
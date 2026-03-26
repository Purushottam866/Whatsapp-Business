package com.whatsApp.wsp_businessAPI.service;

import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.security.UserDetailsImpl;

//@Service
//public class CurrentTenantService {
//
//    private static final Long TEST_TENANT_ID = 999L;   // <-- your tenant
//    private static final Long TEST_USER_ID = 1L;       // any value, only for testing
//
//    public Long getTenantId() {
//
//        var context = SecurityContextHolder.getContext();
//        var auth = context.getAuthentication();
//
//        // -------- NORMAL AUTH FLOW --------
//        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl user) {
//            return user.getTenantId();
//        }
//
//        // -------- TEMP TEST FALLBACK --------
//        // This allows Postman testing without JWT
//        System.out.println("⚠ No authentication found. Using fallback tenantId=999 (TEST MODE)");
//
//        return TEST_TENANT_ID;
//    }
//
//    public Long getUserId() {
//
//        var context = SecurityContextHolder.getContext();
//        var auth = context.getAuthentication();
//
//        // -------- NORMAL AUTH FLOW --------
//        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl user) {
//            return user.getUserId();
//        }
//
//        // -------- TEMP TEST FALLBACK --------
//        System.out.println("⚠ No authentication found. Using fallback userId=1 (TEST MODE)");
//
//        return TEST_USER_ID;
//    }
//}


@Service
public class CurrentTenantService {

    public Long getTenantId() {

        var context = SecurityContextHolder.getContext();
        var auth = context.getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl user)) {
            throw new RuntimeException("Unauthenticated request - JWT required");
        }

        return user.getTenantId();
    }

    public Long getUserId() {

        var context = SecurityContextHolder.getContext();
        var auth = context.getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl user)) {
            throw new RuntimeException("Unauthenticated request - JWT required");
        }

        return user.getUserId();
    }
}
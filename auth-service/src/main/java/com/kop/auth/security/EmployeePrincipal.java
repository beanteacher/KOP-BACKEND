package com.kop.auth.security;

/** JWT에서 뽑아낸 최소 정보 — SecurityContext의 인증 principal로 쓴다. */
public record EmployeePrincipal(String employeeId, String companyId, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}

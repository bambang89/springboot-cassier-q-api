package com.cassierq.api.employee.dto;

import com.cassierq.api.domain.entity.Employee;
import com.cassierq.api.domain.entity.User;
import java.util.List;
import java.util.UUID;

public record EmployeeResponse(
        UUID employeeId,
        UUID userId,
        String employeeCode,
        String name,
        String username,
        String email,
        String phone,
        boolean active,
        List<String> roles) {

    public static EmployeeResponse from(Employee employee, User user, List<String> roleCodes) {
        return new EmployeeResponse(
                employee.getId(),
                user.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                user.getUsername(),
                user.getEmail(),
                employee.getPhone(),
                employee.isActive() && user.isActive(),
                roleCodes);
    }
}

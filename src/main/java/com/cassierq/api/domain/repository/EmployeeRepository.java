package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Employee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    List<Employee> findByStoreId(UUID storeId);
}

package com.emp.management.repository;

import com.emp.management.entity.PortalPermission;
import com.emp.management.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortalPermissionRepository extends JpaRepository<PortalPermission, Long> {
    List<PortalPermission> findByRole(Role role);
    Optional<PortalPermission> findByRoleAndPortalId(Role role, String portalId);
}

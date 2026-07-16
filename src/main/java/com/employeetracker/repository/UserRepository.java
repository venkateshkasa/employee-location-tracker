package com.employeetracker.repository;

import com.employeetracker.entity.User;
import com.employeetracker.entity.UserRole;
import com.employeetracker.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameAndStatus(String username, UserStatus status);

    List<User> findByRole(UserRole role);

    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

    long countByRoleAndStatus(UserRole role, UserStatus status);
}

package dev.thilanka.site_ready.repository;

import dev.thilanka.site_ready.entity.ManagerEngineerAssignment;
import dev.thilanka.site_ready.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ManagerEngineerAssignmentRepository extends JpaRepository<ManagerEngineerAssignment, ManagerEngineerAssignment.AssignmentId> {
    List<ManagerEngineerAssignment> findByManagerId(UUID managerId);

    List<ManagerEngineerAssignment> findByEngineerId(UUID engineerId);

    void deleteByManagerIdAndEngineerId(UUID managerId, UUID engineerId);

    void deleteByManagerId(UUID managerId);

    @Query("SELECT a.engineer FROM ManagerEngineerAssignment a WHERE a.managerId = :managerId")
    List<User> findEngineersByManagerId(@Param("managerId") UUID managerId);

    @Query("SELECT a.managerId FROM ManagerEngineerAssignment a WHERE a.engineerId = :engineerId")
    List<UUID> findManagerIdsByEngineerId(@Param("engineerId") UUID engineerId);

}

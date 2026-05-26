package dev.thilanka.site_ready.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "manager_engineer_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ManagerEngineerAssignment.AssignmentId.class)
public class ManagerEngineerAssignment {
    @Id
    @Column(name = "manager_id")
    private UUID managerId;

    @Id
    @Column(name = "engineer_id")
    private UUID engineerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", insertable = false, updatable = false)
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", insertable = false, updatable = false)
    private User engineer;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentId implements Serializable {
        private UUID managerId;
        private UUID engineerId;
    }
}

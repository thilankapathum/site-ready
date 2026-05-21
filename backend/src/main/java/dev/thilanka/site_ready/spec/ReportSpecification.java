package dev.thilanka.site_ready.spec;

import dev.thilanka.site_ready.entity.Report;
import dev.thilanka.site_ready.entity.enums.ReportStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReportSpecification {

    public static Specification<Report> filter(
            String siteId, String project, String rat,
            ReportStatus status, UUID engineerId,
            UUID vendorId, UUID vendorCompanyId   // ← new
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (siteId != null && !siteId.isBlank())
                predicates.add(cb.like(cb.lower(root.get("siteId")), "%" + siteId.toLowerCase() + "%"));
            if (project != null && !project.isBlank())
                predicates.add(cb.like(cb.lower(root.get("project")), "%" + project.toLowerCase() + "%"));
            if (rat != null && !rat.isBlank())
                predicates.add(cb.like(cb.lower(root.get("rat")), "%" + rat.toLowerCase() + "%"));
            if (status != null)
                predicates.add(cb.equal(root.get("currentStatus"), status));
            if (engineerId != null)
                predicates.add(cb.equal(root.get("assignedEngineer").get("id"), engineerId));
            if (vendorId != null)
                predicates.add(cb.equal(root.get("createdByVendor").get("id"), vendorId));
            if (vendorCompanyId != null)
                predicates.add(cb.equal(root.get("vendorCompany").get("id"), vendorCompanyId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

package dev.thilanka.site_ready.dto;

import dev.thilanka.site_ready.entity.Company;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String shortName,
        String type,
        String country,
        String contactEmail,
        String notes,
        boolean active,
        OffsetDateTime createdAt
) {
    public static CompanyResponse from(Company c) {
        return new CompanyResponse(
                c.getId(), c.getName(), c.getShortName(),
                c.getType().name(), c.getCountry(), c.getContactEmail(),
                c.getNotes(), c.isActive(), c.getCreatedAt()
        );
    }
}

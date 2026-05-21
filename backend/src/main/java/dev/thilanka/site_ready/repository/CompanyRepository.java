package dev.thilanka.site_ready.repository;

import dev.thilanka.site_ready.entity.Company;
import dev.thilanka.site_ready.entity.enums.CompanyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findByActiveTrue();
    List<Company> findByTypeAndActiveTrue(CompanyType type);
    List<Company> findByTypeInAndActiveTrue(List<CompanyType> types);
    boolean existsByName(String name);
    boolean existsByShortName(String shortName);
    Optional<Company> findByShortName(String shortName);
}

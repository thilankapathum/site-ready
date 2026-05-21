package dev.thilanka.site_ready.service;

import dev.thilanka.site_ready.dto.CompanyRequest;
import dev.thilanka.site_ready.dto.CompanyResponse;
import dev.thilanka.site_ready.entity.Company;
import dev.thilanka.site_ready.entity.enums.CompanyType;
import dev.thilanka.site_ready.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    public List<CompanyResponse> listAll() {
        return companyRepository.findAll().stream()
                .map(CompanyResponse::from).toList();
    }

    public List<CompanyResponse> listActive() {
        return companyRepository.findByActiveTrue().stream()
                .map(CompanyResponse::from).toList();
    }

    public List<CompanyResponse> listVendors() {
        return companyRepository.findByTypeAndActiveTrue(CompanyType.VENDOR).stream()
                .map(CompanyResponse::from).toList();
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        if (companyRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("A company with this name already exists.");
        }
        if (companyRepository.existsByShortName(request.shortName())) {
            throw new IllegalArgumentException("A company with this short name already exists.");
        }
        Company company = Company.builder()
                .name(request.name())
                .shortName(request.shortName().toUpperCase())
                .type(request.type())
                .country(request.country())
                .contactEmail(request.contactEmail())
                .notes(request.notes())
                .active(true)
                .build();
        return CompanyResponse.from(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse update(UUID id, CompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        company.setName(request.name());
        company.setShortName(request.shortName().toUpperCase());
        company.setType(request.type());
        company.setCountry(request.country());
        company.setContactEmail(request.contactEmail());
        company.setNotes(request.notes());
        return CompanyResponse.from(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse setActive(UUID id, boolean active) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        company.setActive(active);
        return CompanyResponse.from(companyRepository.save(company));
    }
}

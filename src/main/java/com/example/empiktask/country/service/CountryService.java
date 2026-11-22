package com.example.empiktask.country.service;

import com.example.empiktask.country.exception.CountryAlreadyExistsException;
import com.example.empiktask.country.exception.CountryNotFoundException;
import com.example.empiktask.country.model.Country;
import com.example.empiktask.country.repository.CountryRepository;
import com.example.empiktask.util.CountryCodesUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CountryService {

    private final CountryRepository countryRepository;

    @Transactional
    public Country create(String code, String name) {
        String normalizedCode = CountryCodesUtil.normalize(code);
        int inserted = countryRepository.insertIfAbsent(normalizedCode, name.trim());
        if (inserted == 0) {
            throw new CountryAlreadyExistsException(normalizedCode);
        }
        return countryRepository.findById(normalizedCode).orElseThrow();
    }

    public List<Country> findAll() {
        return countryRepository.findAll(Sort.by("code"));
    }

    public Country findByCode(String code) {
        String normalizedCode = CountryCodesUtil.normalize(code);
        return countryRepository.findById(normalizedCode)
                .orElseThrow(() -> new CountryNotFoundException(normalizedCode));
    }
}

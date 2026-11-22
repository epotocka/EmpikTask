package com.example.empiktask.country.controller;

import com.example.empiktask.country.dto.CountryResponse;
import com.example.empiktask.country.dto.CreateCountryRequest;
import com.example.empiktask.country.model.Country;
import com.example.empiktask.country.service.CountryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;

    @PostMapping
    public ResponseEntity<CountryResponse> create(@Valid @RequestBody CreateCountryRequest request) {
        Country created = countryService.create(request.code(), request.name());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(created.getCode())
                .toUri();
        return ResponseEntity.created(location).body(toResponse(created));
    }

    @GetMapping
    public List<CountryResponse> findAll() {
        return countryService.findAll().stream()
                .map(CountryController::toResponse)
                .toList();
    }

    @GetMapping("/{code}")
    public CountryResponse findByCode(@PathVariable String code) {
        return toResponse(countryService.findByCode(code));
    }

    private static CountryResponse toResponse(Country country) {
        return new CountryResponse(country.getCode(), country.getName());
    }
}

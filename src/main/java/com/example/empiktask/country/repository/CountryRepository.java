package com.example.empiktask.country.repository;

import com.example.empiktask.country.model.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryRepository extends JpaRepository<Country, String> {

    @Modifying
    @Query(value = "INSERT INTO country (code, name) VALUES (:code, :name) ON CONFLICT (code) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("code") String code, @Param("name") String name);
}

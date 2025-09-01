package com.example.onboarding.controller.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JdbcTemplate jdbcTemplate;

    public AdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/migrate")
    public String runMigration() {
        try {
            String sql = Files.readString(Paths.get("migration.sql"));
            jdbcTemplate.execute(sql);
            return "Migration executed successfully";
        } catch (IOException e) {
            return "Error reading migration file: " + e.getMessage();
        } catch (Exception e) {
            return "Error executing migration: " + e.getMessage();
        }
    }

    @PostMapping("/execute-sql")
    public String executeSql(@RequestBody String sql) {
        try {
            jdbcTemplate.execute(sql);
            return "SQL executed successfully";
        } catch (Exception e) {
            return "Error executing SQL: " + e.getMessage();
        }
    }
}
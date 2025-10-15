package com.example.gateway.risk.controller;

import com.example.gateway.risk.service.RiskMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for risk data migration operations.
 * Used for one-time migration from risk_story (v1) to domain_risk + risk_item (v2).
 */
@RestController
@RequestMapping("/api/admin/risk-migration")
public class RiskMigrationController {

    private static final Logger log = LoggerFactory.getLogger(RiskMigrationController.class);

    private final RiskMigrationService migrationService;

    public RiskMigrationController(RiskMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    /**
     * Perform a dry-run migration to see what would be migrated without actually doing it.
     *
     * GET /api/admin/risk-migration/dry-run
     */
    @GetMapping("/dry-run")
    public ResponseEntity<RiskMigrationService.MigrationResult> dryRun() {
        log.info("Starting dry-run migration");

        RiskMigrationService.MigrationResult result = migrationService.migrateAllRisks(true);

        log.info("Dry-run completed: {}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Execute the actual migration from risk_story to domain_risk + risk_item.
     *
     * POST /api/admin/risk-migration/execute
     *
     * WARNING: This will create new domain_risk and risk_item records.
     * Make sure to run dry-run first to preview the changes.
     */
    @PostMapping("/execute")
    public ResponseEntity<RiskMigrationService.MigrationResult> execute() {
        log.warn("Starting ACTUAL migration - this will modify the database");

        RiskMigrationService.MigrationResult result = migrationService.migrateAllRisks(false);

        if (result.isSuccess()) {
            log.info("Migration completed successfully: {}", result);
            return ResponseEntity.ok(result);
        } else {
            log.error("Migration failed: {}", result);
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Backfill status history for already-migrated risk items that are missing history records.
     * Safe to run multiple times - will only create records for items that have no history.
     *
     * POST /api/admin/risk-migration/backfill-status-history
     */
    @PostMapping("/backfill-status-history")
    public ResponseEntity<BackfillResult> backfillStatusHistory() {
        log.info("Starting status history backfill");

        try {
            int backfilledCount = migrationService.backfillStatusHistory();

            BackfillResult result = new BackfillResult();
            result.setSuccess(true);
            result.setBackfilledCount(backfilledCount);
            result.setMessage(String.format("Successfully backfilled status history for %d risk items", backfilledCount));

            log.info("Status history backfill completed: {} records created", backfilledCount);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Status history backfill failed: {}", e.getMessage(), e);

            BackfillResult result = new BackfillResult();
            result.setSuccess(false);
            result.setBackfilledCount(0);
            result.setMessage("Backfill failed: " + e.getMessage());

            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Get database statistics (record counts).
     *
     * GET /api/admin/risk-migration/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<MigrationStats> getStats() {
        // This would query the database for current counts
        // For now, returning a simple response
        MigrationStats stats = new MigrationStats();
        // TODO: Implement actual counting logic
        return ResponseEntity.ok(stats);
    }

    /**
     * Simple stats DTO
     */
    public static class MigrationStats {
        private long riskStoryCount;
        private long domainRiskCount;
        private long riskItemCount;
        private String status;

        public long getRiskStoryCount() {
            return riskStoryCount;
        }

        public void setRiskStoryCount(long riskStoryCount) {
            this.riskStoryCount = riskStoryCount;
        }

        public long getDomainRiskCount() {
            return domainRiskCount;
        }

        public void setDomainRiskCount(long domainRiskCount) {
            this.domainRiskCount = domainRiskCount;
        }

        public long getRiskItemCount() {
            return riskItemCount;
        }

        public void setRiskItemCount(long riskItemCount) {
            this.riskItemCount = riskItemCount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /**
     * Result of backfill operation
     */
    public static class BackfillResult {
        private boolean success;
        private int backfilledCount;
        private String message;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getBackfilledCount() {
            return backfilledCount;
        }

        public void setBackfilledCount(int backfilledCount) {
            this.backfilledCount = backfilledCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

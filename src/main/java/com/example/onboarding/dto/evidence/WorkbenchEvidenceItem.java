package com.example.onboarding.dto.evidence;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public class WorkbenchEvidenceItem {

    @JsonProperty("evidenceId")
    private String evidenceId;

    @JsonProperty("appId")
    private String appId;

    @JsonProperty("appName")
    private String appName;

    @JsonProperty("appCriticality")
    private String appCriticality;

    @JsonProperty("applicationType")
    private String applicationType;

    @JsonProperty("architectureType")
    private String architectureType;

    @JsonProperty("installType")
    private String installType;

    @JsonProperty("applicationTier")
    private String applicationTier;

    @JsonProperty("domainTitle")
    private String domainTitle;

    @JsonProperty("fieldKey")
    private String fieldKey;

    @JsonProperty("fieldLabel")
    private String fieldLabel;

    @JsonProperty("policyRequirement")
    private String policyRequirement;

    @JsonProperty("status")
    private String status;

    @JsonProperty("approvalStatus")
    private String approvalStatus;

    @JsonProperty("freshnessStatus")
    private String freshnessStatus;

    @JsonProperty("dueDate")
    private OffsetDateTime dueDate;

    @JsonProperty("submittedDate")
    private OffsetDateTime submittedDate;

    @JsonProperty("reviewedDate")
    private OffsetDateTime reviewedDate;

    @JsonProperty("rejectionReason")
    private String rejectionReason;

    @JsonProperty("assignedReviewer")
    private String assignedReviewer;

    @JsonProperty("submittedBy")
    private String submittedBy;

    @JsonProperty("daysOverdue")
    private long daysOverdue;

    @JsonProperty("riskCount")
    private int riskCount;

    @JsonProperty("uri")
    private String uri;

    // Getters and Setters

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppCriticality() {
        return appCriticality;
    }

    public void setAppCriticality(String appCriticality) {
        this.appCriticality = appCriticality;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getArchitectureType() {
        return architectureType;
    }

    public void setArchitectureType(String architectureType) {
        this.architectureType = architectureType;
    }

    public String getInstallType() {
        return installType;
    }

    public void setInstallType(String installType) {
        this.installType = installType;
    }

    public String getApplicationTier() {
        return applicationTier;
    }

    public void setApplicationTier(String applicationTier) {
        this.applicationTier = applicationTier;
    }

    public String getDomainTitle() {
        return domainTitle;
    }

    public void setDomainTitle(String domainTitle) {
        this.domainTitle = domainTitle;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public String getPolicyRequirement() {
        return policyRequirement;
    }

    public void setPolicyRequirement(String policyRequirement) {
        this.policyRequirement = policyRequirement;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getFreshnessStatus() {
        return freshnessStatus;
    }

    public void setFreshnessStatus(String freshnessStatus) {
        this.freshnessStatus = freshnessStatus;
    }

    public OffsetDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(OffsetDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public OffsetDateTime getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(OffsetDateTime submittedDate) {
        this.submittedDate = submittedDate;
    }

    public OffsetDateTime getReviewedDate() {
        return reviewedDate;
    }

    public void setReviewedDate(OffsetDateTime reviewedDate) {
        this.reviewedDate = reviewedDate;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getAssignedReviewer() {
        return assignedReviewer;
    }

    public void setAssignedReviewer(String assignedReviewer) {
        this.assignedReviewer = assignedReviewer;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public long getDaysOverdue() {
        return daysOverdue;
    }

    public void setDaysOverdue(long daysOverdue) {
        this.daysOverdue = daysOverdue;
    }

    public int getRiskCount() {
        return riskCount;
    }

    public void setRiskCount(int riskCount) {
        this.riskCount = riskCount;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}

package com.example.onboarding.service;

import com.example.onboarding.integrations.JiraClient;
import com.example.onboarding.integrations.JiraFieldResolver;
import com.example.onboarding.opa.OpaClient;
import com.example.onboarding.opa.OpaModels.OpaInput;
import com.example.onboarding.opa.OpaModels.OpaRequest;
import com.example.onboarding.opa.OpaModels.OpaResponse;
import com.example.onboarding.opa.OpaModels.OpaResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JiraWebhookService {

    private static final Logger log = LoggerFactory.getLogger(JiraWebhookService.class);

    private final ObjectMapper om = new ObjectMapper();
    private final JiraClient jira;
    @SuppressWarnings("unused")
    private final JiraFieldResolver fields; // reserved for future field updates
    private final OpaClient opa;
    private final FormInstanceService forms;

    // Config from application.yml (env overrides supported)
    @Value("${cps.webhook.disable-auth:true}")
    private boolean disableAuth;

    @Value("${cps.webhook.secret:changeme}")
    private String expectedSecret;

    // Risk issue settings (STANDARD issue by NAME, default Story)
    @Value("${cps.risk.issue-type:Story}")
    private String riskIssueType; // e.g., "Risk" if you have a custom type in the project scheme

    @Value("${cps.risk.labels:governance,constraint,risk}")
    private String riskLabelsCsv; // comma-separated

    // Questionnaire settings (STANDARD issue exactly like Risk, default Story)
    @Value("${cps.task.issue-type:Story}")
    private String questionnaireIssueType; // standard issue type (Story/Task/etc)

    @Value("${cps.questionnaire.labels:governance,questionnaire}")
    private String questionnaireLabelsCsv; // comma-separated

    // Attestation settings (STANDARD issue, blocks parent; default Story)
    @Value("${cps.attestation.issue-type:Story}")
    private String attestationIssueType; // standard issue type (Story/Task/etc)

    @Value("${cps.attestation.labels:governance,attestation}")
    private String attestationLabelsCsv; // comma-separated

    public JiraWebhookService(JiraClient jira,
                              JiraFieldResolver fields,
                              OpaClient opa,
                              FormInstanceService forms) {
        this.jira = jira;
        this.fields = fields;
        this.opa = opa;
        this.forms = forms;
    }

    /** Entry point from controller */
    public void process(Map<String, String> headers, String payload) throws Exception {
        if (payload == null || payload.isEmpty()) throw new IllegalArgumentException("Empty webhook payload");
        verifyWebhook(headers);

        JsonNode root = om.readTree(payload);
        String event = root.path("webhookEvent").asText("");
        String issueKey = root.path("issue").path("key").asText("");
        String issueType = root.path("issue").path("fields").path("issuetype").path("name").asText("");
        String projectKey = root.path("issue").path("fields").path("project").path("key").asText("");

        if (issueKey.isEmpty()) throw new IllegalArgumentException("No issue key in payload");
        if (projectKey == null || projectKey.isBlank()) throw new IllegalArgumentException("No project key in payload");

        log.info("Event={} issue={} type={} project={}", event, issueKey, issueType, projectKey);

        // ===== POLICY FLOW (OPA-driven) =====
        log.info(">>> POLICY: starting");
        applyPolicyFlow(issueKey, projectKey, root);
        log.info(">>> POLICY: done");
    }

    /** Toggleable auth (disabled by default) */
    private void verifyWebhook(Map<String, String> headers) {
        if (disableAuth) {
            log.debug("Webhook auth disabled (cps.webhook.disable-auth=true)");
            return;
        }
        String provided = headers.getOrDefault("X-Webhook-Secret", "");
        if (!expectedSecret.equals(provided)) throw new SecurityException("Invalid webhook secret");
        log.debug("Webhook auth passed");
    }

    /**
     * OPA decision:
     * - Risks: create per domain (STANDARD issue) and link Risk blocks Parent (only if domains exist).
     * - Questionnaires: create per domain (STANDARD), add remote link, link Questionnaire ↔ Relates ↔ {Risk, Parent}.
     * - Attestation: when no domains but attestation_required, create STANDARD issue, add remote link, link Attestation blocks Parent.
     * - Leave a single consolidated parent comment summarizing created items.
     */
    private void applyPolicyFlow(String parentIssueKey, String projectKey, JsonNode webhookJson) {
        // 1) Build OPA input
        OpaInput in = new OpaInput();
        in.criticality     = textAt(webhookJson, "issue.fields.criticality");
        in.security        = textAt(webhookJson, "issue.fields.security");
        in.integrity       = textAt(webhookJson, "issue.fields.integrity");
        in.availability    = textAt(webhookJson, "issue.fields.availability");
        in.resilience      = textAt(webhookJson, "issue.fields.resilience");
        in.confidentiality = textAt(webhookJson, "issue.fields.confidentiality");
        in.hasDependencies = boolAt(webhookJson, "issue.fields.has_dependencies");

        // 2) Evaluate policy
        OpaResponse resp = opa.evaluate(new OpaRequest(in));
        if (resp == null || resp.result == null) {
            log.warn("OPA decision unavailable for {}", parentIssueKey);
            return;
        }
        OpaResult r = resp.result;

        log.info("OPA decision for {} => review_mode={} assessment_required={} mandatory={} questionnaire_required={} attestation_required={}",
                parentIssueKey, r.reviewMode, r.assessmentRequired, r.assessmentMandatory,
                r.questionnaireRequired, r.attestationRequired);

        List<String> domains = safeList(r.arbDomains);

        boolean questionnairesNeeded = Boolean.TRUE.equals(r.questionnaireRequired);
        boolean attestationNeeded    = Boolean.TRUE.equals(r.attestationRequired);

        // Collect summary lines (single parent comment later)
        List<String> summaryLines = new ArrayList<>();

        // ---- 2a) Risks per domain (ONLY if domains exist) ----
        Map<String, String> domainToRiskKey = new HashMap<>();
        if (!domains.isEmpty()) {
            for (String domain : domains) {
                String domainSlug = domain.toLowerCase(Locale.ROOT).replace(' ', '-');

                List<String> riskLabels = new ArrayList<>();
                riskLabels.addAll(splitCsv(riskLabelsCsv));
                riskLabels.add(domainSlug);

                String riskSummary = "[Risk][" + domain + "] " + parentIssueKey + " · " + compact(r.reviewMode);
                String triggers = (r.firedRules != null && !r.firedRules.isEmpty())
                        ? String.join("; ", r.firedRules)
                        : "N/A";

                StringBuilder desc = new StringBuilder();
                desc.append("Decision\n");
                desc.append("- Domain: ").append(domain).append("\n");
                desc.append("- Review mode: ").append(nvl(r.reviewMode, "N/A")).append("\n");
                desc.append("- Assessment required: ").append(boolWord(r.assessmentRequired)).append("\n");
                desc.append("- Mandatory: ").append(boolWord(r.assessmentMandatory)).append("\n");
                desc.append("- Attestation required: ").append(boolWord(r.attestationRequired)).append("\n");
                desc.append("- Triggers: ").append(triggers).append("\n\n");
                desc.append("Notes\n");
                desc.append("- This risk blocks ").append(parentIssueKey).append(".\n");

                try {
                    String riskIssueKey = jira.createIssue(projectKey, riskIssueType, riskSummary, desc.toString(), riskLabels);
                    jira.linkIssuesBlocks(riskIssueKey, parentIssueKey); // Risk blocks Parent
                    domainToRiskKey.put(domain, riskIssueKey);
                    summaryLines.add("[" + domain + "] Risk: " + riskIssueKey + " (blocks " + parentIssueKey + ")");
                } catch (Exception e) {
                    log.warn("Failed to create/link risk for domain {} (parent {}): {}", domain, parentIssueKey, e.toString());
                    summaryLines.add("[" + domain + "] Risk: creation failed (" + e.getClass().getSimpleName() + ")");
                }
            }
        }

        // ---- 2b) Questionnaires per domain (when requested AND domains exist) ----
        if (questionnairesNeeded && !domains.isEmpty()) {
            for (String domain : domains) {
                try {
                    String packId = domainToPackId(domain);
                    String packVersion = "v1";

                    // form instance & public URL
                    FormInstance fi = forms.findOrCreate(parentIssueKey, packId, packVersion);
                    String formUrl = forms.publicUrl(fi);

                    String domainSlug = domain.toLowerCase(Locale.ROOT).replace(' ', '-');

                    List<String> qLabels = new ArrayList<>();
                    qLabels.addAll(splitCsv(questionnaireLabelsCsv));
                    qLabels.add(domainSlug);

                    String qSummary = "[Questionnaire][" + domain + "] " + parentIssueKey;
                    StringBuilder qDesc = new StringBuilder();
                    qDesc.append("You are required to complete the ").append(domain).append(" questionnaire.\n\n");
                    qDesc.append("Form: ").append(formUrl).append("\n");
                    String riskKey = domainToRiskKey.get(domain);
                    if (riskKey != null) {
                        qDesc.append("Related risk: ").append(riskKey).append("\n");
                    }
                    qDesc.append("\nAcceptance criteria\n");
                    qDesc.append("- Questionnaire submitted\n");
                    qDesc.append("- Questions answered accurately and completely\n");

                    String questionnaireKey = jira.createIssue(
                            projectKey,
                            questionnaireIssueType, // standard type (Story/Task/etc)
                            qSummary,
                            qDesc.toString(),
                            qLabels
                    );

                    jira.addRemoteLink(questionnaireKey, "Complete " + domain + " questionnaire", formUrl);

                    if (riskKey != null) jira.linkIssuesRelates(questionnaireKey, riskKey); // Questionnaire ↔ Risk
                    jira.linkIssuesRelates(questionnaireKey, parentIssueKey);               // Questionnaire ↔ Parent

                    summaryLines.add("[" + domain + "] Questionnaire: " + questionnaireKey +
                            " (relates to " + (riskKey != null ? riskKey + ", " : "") + parentIssueKey + ")");
                } catch (Exception e) {
                    log.warn("Failed to create/link questionnaire for domain {} (parent {}): {}", domain, parentIssueKey, e.toString());
                    summaryLines.add("[" + domain + "] Questionnaire: creation failed (" + e.getClass().getSimpleName() + ")");
                }
            }
        }

        // ---- 2c) Global Attestation (when NO domains but attestation is required) ----
        if (domains.isEmpty() && attestationNeeded) {
            try {
                String packId = "attestation";
                String packVersion = "v1";

                FormInstance fi = forms.findOrCreate(parentIssueKey, packId, packVersion);
                String formUrl = forms.publicUrl(fi);

                List<String> aLabels = new ArrayList<>(splitCsv(attestationLabelsCsv));

                String aSummary = "[Attestation] " + parentIssueKey + " · " + compact(r.reviewMode);
                StringBuilder aDesc = new StringBuilder();
                aDesc.append("You are required to complete an attestation for this capability.\n\n");
                aDesc.append("Form: ").append(formUrl).append("\n");
                aDesc.append("\nAcceptance criteria\n");
                aDesc.append("- Attestation submitted\n");
                aDesc.append("- Answers are accurate and complete\n");

                String attestationKey = jira.createIssue(
                        projectKey,
                        attestationIssueType, // standard type (Story/Task/etc)
                        aSummary,
                        aDesc.toString(),
                        aLabels
                );

                jira.addRemoteLink(attestationKey, "Complete attestation", formUrl);

                // Attestation should block the parent (mirrors Risk)
                jira.linkIssuesBlocks(attestationKey, parentIssueKey);

                summaryLines.add("Attestation: " + attestationKey + " (blocks " + parentIssueKey + ")");
            } catch (Exception e) {
                log.warn("Failed to create/link attestation for parent {}: {}", parentIssueKey, e.toString());
                summaryLines.add("Attestation: creation failed (" + e.getClass().getSimpleName() + ")");
            }
        }

        // ---- 3) Single consolidated parent comment ----
        if (!summaryLines.isEmpty()) {
            String summary = "Governance actions created:\n" + summaryLines.stream()
                    .map(s -> "- " + s)
                    .collect(Collectors.joining("\n"));
            jira.addComment(parentIssueKey, summary);
        }
    }

    // ---------- helpers ----------

    private static String textAt(JsonNode root, String dottedPath) {
        JsonNode node = walk(root, dottedPath);
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText() : null;
    }

    private static Boolean boolAt(JsonNode root, String dottedPath) {
        JsonNode node = walk(root, dottedPath);
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asBoolean() : null;
    }

    private static JsonNode walk(JsonNode root, String dottedPath) {
        JsonNode cur = root;
        for (String part : dottedPath.split("\\.")) {
            if (cur == null) return null;
            cur = cur.path(part);
        }
        return cur;
    }

    private static String nvl(String v, String dflt) {
        return (v == null || v.isBlank()) ? dflt : v;
    }

    private static String compact(String s) {
        if (s == null) return null;
        return s.replaceAll("\\s+", " ").trim();
    }

    private static List<String> safeList(List<String> v) {
        return v == null ? Collections.emptyList() : v;
    }

    private static String boolWord(Boolean b) {
        if (b == null) return "N/A";
        return b ? "Yes" : "No";
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        return Arrays.stream(csv.split("\\s*,\\s*"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private static String domainToPackId(String domain) {
        switch (domain) {
            case "EA": return "ea-governance";
            case "Security": return "security-governance";
            case "Data": return "data-governance";
            case "Service Transition": return "service-transition";
            default:
                return domain.toLowerCase(Locale.ROOT).replace(' ', '-');
        }
    }
}

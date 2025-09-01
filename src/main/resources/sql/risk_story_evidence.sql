CREATE TABLE risk_story_evidence (
    risk_id TEXT NOT NULL,
    evidence_id TEXT NOT NULL,
    submitted_by TEXT,
    submitted_at TIMESTAMPTZ DEFAULT now(),
    review_status TEXT DEFAULT 'pending',
    reviewed_by TEXT,
    reviewed_at TIMESTAMPTZ,
    review_comment TEXT,
    waiver_reason TEXT,
    waiver_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (risk_id, evidence_id),
    CONSTRAINT fk_risk_id FOREIGN KEY (risk_id) REFERENCES risk_story(risk_id),
    CONSTRAINT fk_evidence_id FOREIGN KEY (evidence_id) REFERENCES evidence(evidence_id)
);
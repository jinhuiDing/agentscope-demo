package com.example.agentscope.service;

import com.example.agentscope.web.ApprovalRecord;
import com.example.agentscope.web.RunRecord;
import com.example.agentscope.web.TraceEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RunTraceStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RunTraceStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
                create table if not exists runs (
                  id varchar(80) primary key,
                  session_id varchar(160),
                  user_id varchar(160),
                  status varchar(40),
                  stage varchar(80),
                  message clob,
                  created_at timestamp,
                  updated_at timestamp,
                  metadata clob
                )
                """);
        jdbc.execute("""
                create table if not exists run_events (
                  event_id identity primary key,
                  run_id varchar(80),
                  session_id varchar(160),
                  user_id varchar(160),
                  type varchar(40),
                  stage varchar(80),
                  content clob,
                  elapsed_ms bigint,
                  created_at timestamp,
                  metadata clob
                )
                """);
        jdbc.execute("""
                create table if not exists approvals (
                  id varchar(80) primary key,
                  run_id varchar(80),
                  status varchar(40),
                  action varchar(80),
                  subject clob,
                  reason clob,
                  created_at timestamp,
                  updated_at timestamp,
                  metadata clob
                )
                """);
    }

    public void createRun(String runId, String sessionId, String userId, String message, Map<String, Object> metadata) {
        Instant now = Instant.now();
        jdbc.update("""
                        merge into runs key(id)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                runId, sessionId, userId, "queued", "queued", message,
                Timestamp.from(now), Timestamp.from(now), toJson(metadata));
    }

    public void updateRun(String runId, String status, String stage) {
        jdbc.update("update runs set status = ?, stage = ?, updated_at = ? where id = ?",
                status, stage, Timestamp.from(Instant.now()), runId);
    }

    public Optional<RunRecord> findRun(String runId) {
        List<RunRecord> rows = jdbc.query("select * from runs where id = ?", this::mapRun, runId);
        return rows.stream().findFirst();
    }

    public List<RunRecord> recentRuns(int limit) {
        return jdbc.query("select * from runs order by updated_at desc limit ?", this::mapRun, safeLimit(limit));
    }

    public void add(String runId, String sessionId, String userId, String type, String stage, String content, long elapsedMs, Map<String, Object> metadata) {
        jdbc.update("""
                        insert into run_events(run_id, session_id, user_id, type, stage, content, elapsed_ms, created_at, metadata)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                runId, sessionId, userId, type, stage, content, elapsedMs,
                Timestamp.from(Instant.now()), toJson(metadata));
        if ("done".equals(type)) {
            updateRun(runId, "completed", "done");
        } else if ("error".equals(type)) {
            updateRun(runId, "failed", "error");
        } else {
            updateRun(runId, "running", stage);
        }
    }

    public List<TraceEntry> recent(int limit) {
        return jdbc.query("select * from run_events order by event_id desc limit ?", this::mapEvent, safeLimit(limit));
    }

    public List<TraceEntry> eventsForRun(String runId, int limit) {
        return jdbc.query("select * from run_events where run_id = ? order by event_id asc limit ?", this::mapEvent, runId, safeLimit(limit));
    }

    public ApprovalRecord createApproval(String runId, String action, String subject, String reason, Map<String, Object> metadata) {
        String id = "approval-" + java.util.UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                        insert into approvals(id, run_id, status, action, subject, reason, created_at, updated_at, metadata)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, runId, "pending", action, subject, reason,
                Timestamp.from(now), Timestamp.from(now), toJson(metadata));
        updateRun(runId, "waiting_approval", "human-review");
        return new ApprovalRecord(id, runId, "pending", action, subject, reason, now, now, metadata);
    }

    public Optional<ApprovalRecord> decideApproval(String approvalId, String status, String reason) {
        jdbc.update("update approvals set status = ?, reason = ?, updated_at = ? where id = ?",
                status, reason, Timestamp.from(Instant.now()), approvalId);
        List<ApprovalRecord> rows = jdbc.query("select * from approvals where id = ?", this::mapApproval, approvalId);
        return rows.stream().findFirst();
    }

    public List<ApprovalRecord> approvalsForRun(String runId) {
        return jdbc.query("select * from approvals where run_id = ? order by created_at asc", this::mapApproval, runId);
    }

    public List<ApprovalRecord> pendingApprovals(int limit) {
        return jdbc.query("select * from approvals where status = 'pending' order by created_at desc limit ?", this::mapApproval, safeLimit(limit));
    }

    private RunRecord mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new RunRecord(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("status"),
                rs.getString("stage"),
                rs.getString("message"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                fromJson(rs.getString("metadata")));
    }

    private TraceEntry mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new TraceEntry(
                rs.getString("run_id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("type"),
                rs.getString("stage"),
                rs.getString("content"),
                rs.getLong("elapsed_ms"),
                toInstant(rs.getTimestamp("created_at")),
                fromJson(rs.getString("metadata")));
    }

    private ApprovalRecord mapApproval(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalRecord(
                rs.getString("id"),
                rs.getString("run_id"),
                rs.getString("status"),
                rs.getString("action"),
                rs.getString("subject"),
                rs.getString("reason"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                fromJson(rs.getString("metadata")));
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }
}

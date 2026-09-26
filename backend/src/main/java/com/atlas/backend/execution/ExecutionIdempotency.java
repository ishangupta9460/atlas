package com.atlas.backend.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared V11 replay store. Callers hold the owner lock and the mutation transaction. */
@Component
public class ExecutionIdempotency {
    private final JdbcTemplate db;
    public ExecutionIdempotency(JdbcTemplate db) { this.db = db; }
    public String replay(Long owner, String key, String fingerprint) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,100}"))
            throw new ExecutionException(400, "A valid Idempotency-Key is required.");
        var rows = db.queryForList("SELECT fingerprint,response_json FROM execution_idempotency WHERE user_id=? AND request_key=?", owner, key);
        if (rows.isEmpty()) return null;
        if (!rows.get(0).get("fingerprint").equals(fingerprint))
            throw ExecutionException.conflict("This request key was already used for another action.");
        return (String) rows.get(0).get("response_json");
    }
    public String save(Long owner, String key, String fingerprint, String json) {
        db.update("INSERT INTO execution_idempotency(user_id,request_key,fingerprint,response_json) VALUES(?,?,?,?)",
                owner, key, fingerprint, json);
        return json;
    }
}

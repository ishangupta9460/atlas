package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Expand the existing check without rewriting historical blocks. */
public class V14__recovery_block_state extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        boolean mysql = context.getConnection().getMetaData().getDatabaseProductName().equals("MySQL");
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE scheduled_blocks DROP " + (mysql ? "CHECK " : "CONSTRAINT ") + "chk_blocks_state");
            statement.execute("ALTER TABLE scheduled_blocks ADD CONSTRAINT chk_blocks_state CHECK (state IN ('scheduled','active','completed','superseded','unresolved','cancelled'))");
        }
    }
}

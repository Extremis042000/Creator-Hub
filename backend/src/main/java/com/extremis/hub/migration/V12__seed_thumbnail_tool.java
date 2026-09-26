package com.extremis.hub.migration;

import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Phase 41. Same app-generated-UUID rationale as V2__seed_tools --
 * kept as its own migration rather than appending to V2, since V2
 * already ran on the live database and Flyway migrations are
 * immutable once applied. Unlike every tool V2 seeded, this one is
 * inserted with premium_only=true from the start: there's no
 * deterministic template fallback for an image (see
 * docs/decisions/10-ai-thumbnail-generator.md Sec4), so this tool is
 * premium-gated from day one, not toggled on later via the admin
 * panel the way gaming-description-generator was.
 */
public class V12__seed_thumbnail_tool extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (var stmt = context.getConnection().prepareStatement(
                "INSERT INTO tool (id, tool_type, slug, name, category, premium_only) VALUES (?, ?, ?, ?, ?, true)")) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setString(2, "THUMBNAIL_GENERATOR");
            stmt.setString(3, "gaming-thumbnail-generator");
            stmt.setString(4, "Gaming YouTube Thumbnail Generator");
            stmt.setString(5, "creator");
            stmt.executeUpdate();
        }
    }
}

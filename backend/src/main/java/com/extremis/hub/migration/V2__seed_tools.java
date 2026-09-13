package com.extremis.hub.migration;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * IDs are generated in application code (UUID.randomUUID()), not via
 * gen_random_uuid(), to avoid a Postgres extension/version
 * dependency — see docs/07-phase2-system-design.md's UUID generation
 * strategy note. This is the one, single seeding mechanism for rows
 * that need an app-generated UUID.
 */
public class V2__seed_tools extends BaseJavaMigration {

    private record Row(String toolType, String slug, String name, String category) {}

    @Override
    public void migrate(Context context) throws Exception {
        List<Row> rows = List.of(
            new Row("KD_CALCULATOR", "kd-calculator", "KD Ratio Calculator", "competitive"),
            new Row("VALORANT_SENSITIVITY_CONVERTER", "valorant-sensitivity-converter", "Valorant Sensitivity Converter", "competitive"),
            new Row("BGMI_SENSITIVITY_HELPER", "bgmi-sensitivity-helper", "BGMI Sensitivity Helper", "competitive"),
            new Row("TITLE_GENERATOR", "gaming-title-generator", "Gaming YouTube Title Generator", "creator"),
            new Row("DESCRIPTION_GENERATOR", "gaming-description-generator", "Gaming YouTube Description Generator", "creator")
        );

        try (var stmt = context.getConnection().prepareStatement(
                "INSERT INTO tool (id, tool_type, slug, name, category, premium_only) VALUES (?, ?, ?, ?, ?, false)")) {
            for (Row r : rows) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setString(2, r.toolType());
                stmt.setString(3, r.slug());
                stmt.setString(4, r.name());
                stmt.setString(5, r.category());
                stmt.executeUpdate();
            }
        }
    }
}

package common;

import io.github.str4ng3r.common.Constants;
import io.github.str4ng3r.common.Update;

/**
 * Reusable Update builders shared across dialect-specific integration tests.
 *
 * @author Pablo Eduardo Martinez Solis
 */
public class UpdateTest {

    /**
     * Updates a single column (name) for the user with the given id.
     */
    public static Update updateName(Constants.SqlDialect dialect, int id, String newName) {
        return new Update()
                .from("users")
                .setColumnsValuesToUpdate(p -> p.put("name", newName))
                .where("id = :id", p -> p.put("id", id))
                .setDialect(dialect);
    }

    /**
     * Updates name but excludes password from the SET clause even if present in
     * the value map, to verify excludeColumns.
     */
    public static Update updateExcludingPassword(Constants.SqlDialect dialect, int id, String newName, String password) {
        return new Update()
                .from("users")
                .excludeColumns("password")
                .setColumnsValuesToUpdate(p -> {
                    p.put("name", newName);
                    p.put("password", password);
                })
                .where("id = :id", p -> p.put("id", id))
                .setDialect(dialect);
    }
}

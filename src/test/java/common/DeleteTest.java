package common;

import io.github.str4ng3r.common.Constants;
import io.github.str4ng3r.common.Delete;
import dao.UserDao;

/**
 * Reusable Delete builders (and entity factories for entity-based deletes)
 * shared across dialect-specific integration tests.
 *
 * @author Pablo Eduardo Martinez Solis
 */
public class DeleteTest {

    /**
     * Delete-by-id builder for the users table. The soft/hard behavior is decided
     * by the boolean passed to Runner.delete(delete, hardDelete).
     */
    public static Delete deleteUserById(Constants.SqlDialect dialect, int id) {
        return new Delete()
                .from("users")
                .where("id = :id", p -> p.put("id", id))
                .setDialect(dialect);
    }

    /**
     * A UserDao carrying only the id, for entity-based deletes: delete(user, hardDelete).
     */
    public static UserDao userWithId(int id) {
        UserDao u = new UserDao();
        u.setId(id);
        return u;
    }
}

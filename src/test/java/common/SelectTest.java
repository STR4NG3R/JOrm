package common;

import io.github.str4ng3r.common.Constants;
import io.github.str4ng3r.common.Join;
import io.github.str4ng3r.common.Selector;

/**
 * Reusable Selector builders shared across dialect-specific integration tests.
 * The SQL dialect is passed in so the same helpers work for Postgres, MySQL and Oracle.
 *
 * @author Pablo Eduardo Martinez Solis
 */
public class SelectTest {

    /**
     * Users query with optional filters and joins to address tables.
     */
    public static Selector baseQueryUsers(Constants.SqlDialect dialect, String name, String lastName, String cp) {
        Selector s = new Selector()
                .select("users as u",
                        "u.id id", "u.name name", "u.email email", "u.role role",
                        "u.email as email")
                .join(Join.LEFT, "userAddress as ua", "u.id = ua.userId")
                .join(Join.INNER, "addresses as a", "a.id = ua.addressId")
                .setDialect(dialect);

        if (name != null)
            s.andWhere("u.name LIKE CONCAT('%', :name, '%')", parameters -> parameters.put("name", name));

        if (lastName != null)
            s.andWhere("u.lastName LIKE CONCAT('%', :lastName, '%')", parameters -> parameters.put("lastName", lastName));

        if (cp != null)
            s.andWhere("a.cp = :cp", parameters -> parameters.put("cp", cp));

        return s;
    }

    /**
     * Simple single-table selector fetching a user by id, including audit columns.
     */
    public static Selector getUserById(Constants.SqlDialect dialect, int id) {
        return new Selector()
                .select("users",
                        "id", "name", "email", "password",
                        "role", "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("id = :id", (p) -> p.put("id", id))
                .setDialect(dialect);
    }

    /**
     * Selector over the users table with the standard column set, for paginated
     * or WHERE-IN style queries. No joins, so soft-delete filtering is predictable.
     */
    public static Selector allUsers(Constants.SqlDialect dialect) {
        return new Selector()
                .select("users",
                        "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("id > :id", p -> p.put("id", 0))
                .setDialect(dialect);
    }

    /**
     * Selector filtering by a single-column set (WHERE IN).
     */
    public static Selector usersByIds(Constants.SqlDialect dialect, java.util.Collection<Integer> ids) {
        return new Selector()
                .select("users",
                        "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("id IN (:ids)", p -> p.put("ids", ids))
                .setDialect(dialect);
    }

    /**
     * Selector by email, useful to verify inserts.
     */
    public static Selector userByEmail(Constants.SqlDialect dialect, String email) {
        return new Selector()
                .select("users",
                        "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", email))
                .setDialect(dialect);
    }
}

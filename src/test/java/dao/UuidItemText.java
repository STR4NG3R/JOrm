package dao;

import io.github.str4ng3r.*;

import java.sql.Timestamp;

/** Same table as {@link UuidItem}, but the uuid is mapped to a plain String. */
@Entity(name = "uuid_items")
public class UuidItemText {
    @Id
    String id;
    @Column
    String name;
    @CreatedAt
    Timestamp createdAt;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

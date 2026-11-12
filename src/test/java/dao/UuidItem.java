package dao;

import io.github.str4ng3r.*;

import java.sql.Timestamp;
import java.util.UUID;

/** Entity whose primary key is a {@link UUID}. */
@Entity(name = "uuid_items")
public class UuidItem {
    @Id
    UUID id;
    @Column
    String name;
    @CreatedAt
    Timestamp createdAt;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

# JOrm

**JOrm** is a lightweight Java ORM focused on simplicity, performance, and minimal footprint.

It is designed for environments where large ORMs are not ideal, such as:

- Serverless platforms (AWS Lambda, Cloud Functions)
- Microservices
- Lightweight APIs
- Applications that require direct SQL control

JOrm weighs **less than 80KB** and has **no external dependencies**, making it ideal for **serverless environments**.

---

# Features

- Lightweight (<80kb)
- No dependencies
- Built on top of JDBC
- Query Builder support
- Optional manual mapping
- Automatic entity mapping
- Pagination support
- Soft Delete support
- Works well in **serverless environments**

---

# Installation

Download the latest release from Maven Central.

Example Maven dependency:

```xml
<dependency>
    <groupId>io.github.str4ng3r</groupId>
    <artifactId>jorm</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

# Entity Example

```java
@Entity(name = "users")
public class UserDao {

    @Id
    Integer id;

    @Column
    String name;

    @Column
    String email;

    @Column
    String role;

    @Column
    String password;

    @UpdatedAt
    Timestamp updatedAt;

    @DeletedAt
    Timestamp deletedAt;

    @CreatedAt
    Timestamp createdAt;
}
```

---

# SELECT Example

Using a query builder:

```java
Selector selector = new Selector()
        .select("users",
                "id",
                "name",
                "email",
                "role",
                "updatedAt as \"updatedAt\"",
                "createdAt as \"createdAt\"",
                "deletedAt as \"deletedAt\"")
        .where("id = :id", p -> p.put("id", 1));

List<UserDao> users = new Runner<UserDao>(connection)
        .select(selector, UserDao.class);
```

---

# SELECT with Manual Mapping

Manual mapping is also supported if you prefer full control.

```java
List<UserDao> users = new Runner<UserDao>(connection)
        .select(
                selector,
                rs -> new UserDao(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email")
                )
        );
```

---

# Pagination

JOrm supports paginated queries using `selectPaginated`.

```java
Template<List<UserDao>> paginated = new Runner<UserDao>(connection)
        .selectPaginated(
                1,
                10,
                selector,
                UserDao.class
        );
```

You can access the results like this:

```java
List<UserDao> users = paginated.getData();
```

---

# Pagination Response Structure

The `selectPaginated` method returns a `Template<T>` object.

`Template<T>` extends the `Pagination` class and wraps the paginated result data.

```java
public class Template<T> extends Pagination {

    T data;

    public Template(SqlParameter sqlParameter, T data) {
        super(sqlParameter.p);
        this.data = data;
    }

    public T getData() {
        return this.data;
    }
}
```

The response contains both **data and pagination metadata**.

Conceptual example:

```text
{
  data: [UserDao, UserDao, UserDao, ...],
  currentPage: 1,
  pageSize: 10,
  count: 120,
  totalPages: 12
}
```

This structure allows easy integration with APIs and UI pagination components.

---

# Insert / Update (Upsert Behavior)

JOrm supports **upsert-style operations** using the `insert()` method.

If the entity contains a non-null `@Id`, JOrm will:

1. Perform a lookup to determine if the record exists
2. If the record exists → perform an **UPDATE**
3. If the record does not exist → perform an **INSERT**

Example:

```java
UserDao user = new UserDao();
user.setId(1);
user.setName("Updated User Name");

new Runner<UserDao>()
        .insert(UserDao.class, user);
```

Behavior:

If the record exists:

```sql
UPDATE users
SET name = ?
WHERE id = ?
```

If the record does not exist:

```sql
INSERT INTO users (id, name)
VALUES (?, ?)
```

This allows a single operation to handle both **create and update workflows**.

---

# Dynamic Query Builder Example

JOrm's Query Builder allows you to dynamically construct SQL queries based on optional parameters.

Example with conditional filters and joins:

```java
public static Selector baseQueryUsers(String name, String lastName, String cp) {

    Selector selector = new Selector()
            .select(
                    "users as u",
                    "u.id id",
                    "u.name name",
                    "u.email email",
                    "u.role role"
            )
            .join(Join.JOIN.LEFT, "userAddress as ua", "u.id = ua.userId")
            .join(Join.JOIN.INNER, "addresses as a", "a.id = ua.addressId");

    if (name != null) {
        selector.andWhere(
                "u.name LIKE CONCAT('%', :name, '%')",
                p -> p.put("name", name)
        );
    }

    if (lastName != null) {
        selector.andWhere(
                "u.lastName LIKE CONCAT('%', :lastName, '%')",
                p -> p.put("lastName", lastName)
        );
    }

    if (cp != null) {
        selector.andWhere(
                "a.cp = :cp",
                p -> p.put("cp", cp)
        );
    }

    return selector;
}
```

Example usage:

```java
Selector selector = baseQueryUsers("John", null, "64000");

List<UserDao> users = new Runner<UserDao>()
        .select(selector, UserDao.class);
```

---

# Benefits of Dynamic Query Building

Using dynamic query construction provides several advantages:

### Flexible Filtering

Optional parameters allow you to build queries dynamically without writing multiple SQL variants.

Example:

- Only filter by name
- Filter by name and postal code
- Filter by postal code only

All using the same query builder.

---

### Cleaner Code

Instead of concatenating SQL strings manually, conditions are added in a structured way.

---

### Safer Parameter Handling

All parameters are bound using named parameters, reducing the risk of SQL injection.

---

### Reusable Query Definitions

Base queries can be reused across different services or APIs and extended with additional conditions when needed.
---

# UPDATE Example

If you need more control over your updates you can use query builders to perform updates
```java
new Runner<Void>(connection)
        .update(
                new Update()
                        .table("users")
                        .set("name = :name", p -> p.put("name", "Updated Name"))
                        .where("id = :id", p -> p.put("id", 1))
        );
```

---

# DELETE Example (Query Builder)

```java
new Runner<Void>(connection)
        .delete(
                new Delete()
                        .from("users")
                        .where("id = :id", p -> p.put("id", 1)),
                false
        );
```

The second parameter determines whether the delete is **soft** or **hard**.

---

# Delete Using Entity

```java
UserDao user = new UserDao();
user.setId(1);

new Runner<UserDao>(connection)
        .delete(user, false);
```

---

# Soft Delete

JOrm supports soft delete using the `@DeletedAt` annotation.

```java
@DeletedAt
Timestamp deletedAt;
```

When performing a delete with `hardDelete = false`, JOrm will execute an update instead of removing the record.

Example SQL behavior:

```sql
UPDATE users SET deletedAt = NOW() WHERE id = ?
```

---

# Querying Soft Deleted Records

By default, soft deleted records are excluded.

To include them:

```java
List<UserDao> users = new Runner<UserDao>()
        .withDeleted(true)
        .select(selector, UserDao.class);
```

---

# Example Query Builder with Joins

```java
Selector selector = new Selector()
        .select(
                "users as u",
                "u.id id",
                "u.name name",
                "u.email email",
                "u.role role"
        )
        .join(Join.JOIN.LEFT, "userAddress as ua", "u.id = ua.userId")
        .join(Join.JOIN.INNER, "addresses as a", "a.id = ua.addressId")
        .andWhere("u.name LIKE CONCAT('%', :name, '%')",
                p -> p.put("name", "John"));
```

---

# Why JOrm?

JOrm focuses on **performance and minimalism**.

Unlike traditional ORMs, it does not attempt to abstract SQL entirely.

Instead it provides:

- Simple entity mapping
- A lightweight query builder
- Full control over SQL
- Minimal runtime overhead

Because of its **very small footprint (<80KB)** and **no dependencies**, JOrm is particularly suitable for:

- AWS Lambda
- Serverless architectures
- High-performance microservices
- Lightweight APIs

---

# License

MIT License

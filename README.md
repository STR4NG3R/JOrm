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

# INSERT Example

```java
UserDao user = new UserDao();
user.setEmail("user@email.com");
user.setRole("Admin");
user.setName("Pablo");
user.setPassword("secret");

new Runner<UserDao>(connection)
        .insert(UserDao.class, user);
```

---

# UPDATE Example

Currently, updates are performed using the Query Builder.

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

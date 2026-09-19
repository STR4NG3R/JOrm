# JOrm

**JOrm** is a lightweight Java ORM focused on simplicity, performance, and minimal footprint.

Designed for environments where heavy ORMs are not ideal:

- ☁️ Serverless platforms (AWS Lambda, Cloud Functions)
- 🔬 Microservices
- ⚡ Lightweight APIs
- 🎯 Applications that require direct SQL control

> **Less than 80KB. Single lightweight dependency, built on JDBC.**

---

## Features

| Feature | Description |
|---|---|
| 🪶 Lightweight | Less than 80KB jar |
| 🔗 Single dependency | Only the zero-dependency query builder, on top of JDBC |
| 🔨 Query Builder | Fluent API for SELECT, INSERT, UPDATE, DELETE |
| 🗺️ Auto mapping | Automatic entity mapping via annotations |
| ✋ Manual mapping | Full control via `ResultSet` consumer |
| 📄 Pagination | Built-in paginated queries with metadata |
| 🔀 Upsert | Smart insert — updates if record exists |
| 📦 Batch Insert | Efficient bulk inserts with configurable batch size |
| 🗑️ Soft Delete | `@DeletedAt` annotation, transparent filtering |
| 🔗 Joins | INNER, LEFT, RIGHT and CROSS joins in the query builder |
| 📥 WHERE IN | Bind a collection and it expands to one `?` per element |
| 🔁 Transactions | Callback-style or manual commit/rollback with isolation levels |
| 📊 Metrics | Per-query performance tracking with slow query detection |

---

## Installation

```xml
<dependency>
    <groupId>io.github.str4ng3r</groupId>
    <artifactId>jorm</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Entity

Annotate your class with `@Entity` and mark fields with the provided annotations:

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

    @CreatedAt
    Timestamp createdAt;

    @UpdatedAt
    Timestamp updatedAt;

    @DeletedAt
    Timestamp deletedAt;
}
```

| Annotation | Behavior |
|---|---|
| `@Id` | Marks the primary key |
| `@Column` | Mapped column, included in INSERT/UPDATE |
| `@CreatedAt` | Auto-set on insert |
| `@UpdatedAt` | Auto-set on update |
| `@DeletedAt` | Used for soft delete |

---

## SELECT

```java
Selector selector = new Selector()
        .select("users",
                "id", "name", "email", "role",
                "updatedAt as \"updatedAt\"",
                "createdAt as \"createdAt\"",
                "deletedAt as \"deletedAt\"")
        .where("id = :id", p -> p.put("id", 1));

List<UserDao> users = new Runner<UserDao>(connection)
        .select(selector, UserDao.class);
```

### Manual Mapping

When you need full control over the mapping:

```java
List<UserDao> users = new Runner<UserDao>(connection)
        .select(selector, rs -> new UserDao(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("email")
        ));
```

If you use the same mapping logic in multiple places, define it once as a constant on the entity itself to avoid repetition:

```java
@Entity(name = "users")
public class UserDao {

    @Id Integer id;
    @Column String name;
    @Column String email;

    // define the mapper once, reuse everywhere
    public static final Function<ResultSet, UserDao> MAPPER = rs -> {
        try {
            return new UserDao(
                    rs.getInt("id"),
                    rs.getString("name").toUpperCase(), // custom conversion
                    rs.getString("email")
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    };
}
```

```java
// reuse across different selectors without repeating the lambda
List<UserDao> users  = runner.select(selectorA, UserDao.MAPPER);
List<UserDao> admins = runner.select(selectorB, UserDao.MAPPER);

Template<List<UserDao>> page = runner.selectPaginated(1, 10, selector, UserDao.MAPPER);
```

This keeps mapping logic in one place without coupling the entity to the ORM — `UserDao` remains a plain object, the mapper is just a static field.

---

## Pagination

```java
Template<List<UserDao>> page = new Runner<UserDao>(connection)
        .selectPaginated(1, 10, selector, UserDao.class);

List<UserDao> users = page.getData();
```

The `Template<T>` response includes pagination metadata alongside the data:

```json
{
  "data": [...],
  "currentPage": 1,
  "pageSize": 10,
  "count": 120,
  "totalPages": 12
}
```

Manual mapping is also supported in paginated queries:

```java
Template<List<UserDao>> page = new Runner<UserDao>(connection)
        .selectPaginated(1, 10, selector, rs -> new UserDao(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("email")
        ));
```

---

## Insert / Update (Upsert)

`insert()` behaves as an upsert — if the entity has a non-null `@Id`, JOrm checks if the record exists first:

- Record **exists** → `UPDATE`
- Record **does not exist** → `INSERT`

```java
// INSERT — no id set
UserDao user = new UserDao();
user.setName("Alice");
user.setEmail("alice@example.com");
user.setRole("user");

new Runner<UserDao>(connection)
        .insert(UserDao.class, user);
```

```java
// UPSERT — id is set, JOrm checks first
UserDao user = new UserDao();
user.setId(1);
user.setName("Updated Name");

new Runner<UserDao>(connection)
        .insert(UserDao.class, user);
```

> **Note:** When an `@Id` is present, JOrm performs a `SELECT` before deciding between INSERT and UPDATE.
> Avoid using this pattern in tight loops or batch scenarios — use `insert(clazz, list, batchSize)` instead.

`@CreatedAt` is set automatically on insert. `@UpdatedAt` is set automatically on update.

---

## Batch Insert

Insert large collections efficiently with a configurable batch size:

```java
List<UserDao> users = buildUserList(); // any size

new Runner<UserDao>(connection)
        .insert(UserDao.class, users, 50); // flush every 50 rows
```

JOrm handles partial commits automatically — if the list size is not a multiple of `batchSize`, the remaining records are committed at the end.

---

## UPDATE

Use the `Update` builder for explicit updates with full control:

```java
new Runner<Void>(connection)
        .update(
                new Update()
                        .from("users")
                        .setColumnsValuesToUpdate(p -> p.put("name", "Updated Name"))
                        .where("id = :id", p -> p.put("id", 1))
        );
```

### Excluding columns

`excludeColumns(...)` removes columns from the generated `SET` clause, even if you added them to the value map. This is handy when you build the column map generically but want to keep certain fields untouched (for example, never overwrite `password`):

```java
new Runner<Void>(connection)
        .update(
                new Update()
                        .from("users")
                        .excludeColumns("password") // never updated
                        .setColumnsValuesToUpdate(p -> {
                            p.put("name", "Ana");
                            p.put("password", "secret"); // dropped from SET
                        })
                        .where("id = :id", p -> p.put("id", 1))
        );
// UPDATE users SET name = ? WHERE id = ?
// parameters: [Ana, 1]   (password is excluded)
```

---

## DELETE

### Query Builder

```java
new Runner<Void>(connection)
        .delete(
                new Delete()
                        .from("users")
                        .where("id = :id", p -> p.put("id", 1)),
                false // false = soft delete, true = hard delete
        );
```

### Entity

```java
UserDao user = new UserDao();
user.setId(1);

new Runner<UserDao>(connection)
        .delete(user, false);
```

---

## Soft Delete

Add `@DeletedAt` to your entity and JOrm handles the rest transparently.

```java
@DeletedAt
Timestamp deletedAt;
```

When `hardDelete = false`, JOrm executes an UPDATE instead of DELETE:

```sql
UPDATE users SET deletedAt = NOW() WHERE id = ?
```

Soft-deleted records are **excluded by default** from all queries. To include them:

```java
List<UserDao> all = new Runner<UserDao>(connection)
        .withDeleted(true)
        .select(selector, UserDao.class);
```

---

## Dynamic Query Builder

Build queries conditionally without string concatenation:

```java
public static Selector userQuery(String name, String lastName, String postalCode) {

    Selector selector = new Selector()
            .select("users as u", "u.id", "u.name", "u.email", "u.role")
            .join(Join.LEFT,  "userAddress as ua", "u.id = ua.userId")
            .join(Join.INNER, "addresses as a",    "a.id = ua.addressId");

    if (name != null)
        selector.andWhere("u.name LIKE CONCAT('%', :name, '%')",
                p -> p.put("name", name));

    if (lastName != null)
        selector.andWhere("u.lastName LIKE CONCAT('%', :lastName, '%')",
                p -> p.put("lastName", lastName));

    if (postalCode != null)
        selector.andWhere("a.cp = :cp",
                p -> p.put("cp", postalCode));

    return selector;
}
```

All parameters are bound as named parameters — no string interpolation, no SQL injection risk.

---

## WHERE IN

Bind a `Collection` to a named parameter and JOrm expands it to one `?` per element automatically:

```java
Selector selector = new Selector()
        .select("users", "id", "name", "email")
        .where("id IN (:ids)", p -> p.put("ids", Arrays.asList(1, 2, 3)));

List<UserDao> users = new Runner<UserDao>(connection)
        .select(selector, UserDao.class);
// SELECT id, name, email FROM users WHERE id IN (?,?,?)
// parameters: [1, 2, 3]
```

A single-element collection works the same way, expanding to a single placeholder. Values are always bound as parameters — never concatenated into the SQL.

---

## Transactions

JOrm provides full transaction support directly on the `Runner`. All operations share the same `Connection`, so wrapping them in a transaction is straightforward.

### Automatic — callback style (recommended)

The `transaction()` method handles commit and rollback for you:

```java
new Runner<Void>(connection)
        .transaction(runner -> {
            runner.insert(UserDao.class, user);
            runner.insert(OrderDao.class, order);
            // any exception here triggers automatic rollback
        });
```

If the callback throws, JOrm calls `rollback()` and rethrows the exception wrapped in `SQLException`. If it succeeds, JOrm calls `commit()` automatically.

### With isolation level

```java
new Runner<Void>(connection)
        .transaction(Runner.ISOLATION.SERIALIZABLE, runner -> {
            runner.insert(UserDao.class, user);
            runner.update(new Update()...);
        });
```

Available isolation levels:

| Level | Constant |
|---|---|
| None | `ISOLATION.NONE` |
| Read Uncommitted | `ISOLATION.READ_UNCOMMITTED` |
| Read Committed | `ISOLATION.READ_COMMITTED` |
| Repeatable Read | `ISOLATION.REPEATABLE_READ` |
| Serializable | `ISOLATION.SERIALIZABLE` |

### Manual control

For more granular control you can manage the transaction lifecycle yourself:

```java
Runner<Void> runner = new Runner<>(connection);
runner.beginTransaction();

try {
    runner.insert(UserDao.class, user);
    runner.update(new Update()...);
    runner.commit();
} catch (Exception e) {
    runner.rollback();
    throw e;
}
```

---

## Concurrency & Connection Management

JOrm operates directly on a raw JDBC `Connection` — it never opens or closes it, it only uses it. This keeps the library tiny and predictable, but it means **connection lifecycle and concurrency are your responsibility**.

### The threading model in one rule

> **A `Runner` and its `Connection` are not thread-safe. Use one `Runner` (and one `Connection`) per thread / per request.**

What *is* safe to share across threads:

- The entity registry and reflection cache (`ConcurrentHashMap`, populated idempotently)
- The global [Metrics](#metrics) singleton

What is **not** safe to share:

- A `Runner` instance — it holds mutable per-instance state (`alias`, `withDeleted`, `hardDelete`, transaction flags)
- A `java.sql.Connection` — JDBC connections are single-threaded by contract, and transactions mutate `autoCommit` on the connection

So in any concurrent application you should pull a fresh connection from a **connection pool** for each unit of work, wrap it in a `Runner`, and release it when done. Creating a `Runner` is cheap — it's just a thin wrapper.

### Plain JDBC

Use a pool (HikariCP is the recommended choice — small, fast, and aligned with JOrm's philosophy) and grab one connection per operation. The try-with-resources block returns the connection to the pool automatically:

```java
// Once, at application startup — a single shared pool
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
config.setUsername("user");
config.setPassword("pass");
config.setMaximumPoolSize(10);
HikariDataSource dataSource = new HikariDataSource(config);
```

```java
// Per operation / per thread — never share this connection
try (Connection conn = dataSource.getConnection()) {
    List<UserDao> users = new Runner<UserDao>(conn)
            .select(selector, UserDao.class);
} // connection is returned to the pool here
```

> Do **not** cache a single `Connection` in a static field and reuse it from multiple threads. Always take one from the pool per unit of work.

### Spring

Spring already manages a pooled `DataSource` (HikariCP by default). Inject it and open a connection per operation. Let Spring's `DataSourceUtils` hand you the connection so JOrm participates in Spring-managed transactions when present:

```java
@Repository
public class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<UserDao> findAll(Selector selector) throws SQLException {
        // DataSourceUtils returns the transaction-bound connection if one exists,
        // otherwise a fresh pooled connection.
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            return new Runner<UserDao>(conn).select(selector, UserDao.class);
        } finally {
            // Releases to the pool only if it's not bound to an active transaction.
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }
}
```

For write paths you can rely on Spring's declarative transactions and let JOrm run inside them:

```java
@Transactional
public void createUser(UserDao user) throws SQLException {
    Connection conn = DataSourceUtils.getConnection(dataSource);
    try {
        new Runner<UserDao>(conn).insert(UserDao.class, user);
        // Spring commits/rolls back the transaction; don't call commit() here.
    } finally {
        DataSourceUtils.releaseConnection(conn, dataSource);
    }
}
```

> When running inside a Spring `@Transactional` method, let **Spring** own commit/rollback. Use JOrm's own `transaction(...)` / `beginTransaction()` only when you are managing the connection yourself (plain JDBC, no Spring transaction manager).

#### Using JOrm in a `@Service`

The `@Repository` pattern above is not the only option. Inside a `@Service` you have three valid approaches depending on **who owns the transaction**.

**Option A — `@Service` + Spring `@Transactional` (Spring owns the transaction).**
The idiomatic choice when you already use Spring's transactional stack. Take the connection with `DataSourceUtils` so every operation joins the same Spring-managed transaction, and let Spring commit/rollback. Do **not** call `commit()`/`rollback()` yourself.

```java
@Service
public class UserService {

    private final DataSource dataSource;

    public UserService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional
    public void register(UserDao user, OrderDao firstOrder) throws SQLException {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            new Runner<UserDao>(conn).insert(UserDao.class, user);
            new Runner<OrderDao>(conn).insert(OrderDao.class, firstOrder);
            // No commit/rollback here — Spring does it when the method returns.
            // Any thrown exception triggers an automatic rollback.
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    @Transactional(readOnly = true)
    public List<UserDao> findAll(Selector selector) throws SQLException {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            return new Runner<UserDao>(conn).select(selector, UserDao.class);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }
}
```

Use this when a transaction spans multiple repositories/services, or when you mix JOrm with JPA/`JdbcTemplate` in the same transaction.

**Option B — `@Service` without `@Transactional` (JOrm owns the transaction).**
If you'd rather not depend on Spring's transaction manager, pull a connection from the pool and use JOrm's own `transaction(...)`. Here JOrm commits/rolls back.

```java
@Service
public class UserService {

    private final DataSource dataSource;

    public UserService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void register(UserDao user, OrderDao firstOrder) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {   // pooled connection
            new Runner<Void>(conn).transaction(runner -> {
                runner.insert(UserDao.class, user);
                runner.insert(OrderDao.class, firstOrder);
                // any exception here triggers automatic rollback
            });
        }
    }

    // With an isolation level
    public void registerSerializable(UserDao user) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            new Runner<Void>(conn).transaction(Runner.ISOLATION.SERIALIZABLE, runner -> {
                runner.insert(UserDao.class, user);
            });
        }
    }
}
```

Here you use `dataSource.getConnection()` directly (not `DataSourceUtils`), because you are **not** inside a Spring transaction. Do not combine this with `@Transactional` on the same method — you'd have two transaction managers fighting over the same connection.

**Option C — read-only `@Service`, no explicit transaction.**
Simple reads need no transaction at all. Just take a connection per operation and close it.

```java
@Service
public class UserService {

    private final DataSource dataSource;

    public UserService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<UserDao> findByRole(String role) throws SQLException {
        Selector selector = new Selector()
                .select("users", "id", "name", "email", "role")
                .where("role = :role", p -> p.put("role", role));

        try (Connection conn = dataSource.getConnection()) {
            return new Runner<UserDao>(conn).select(selector, UserDao.class);
        }
    }
}
```

**Choosing between them:**

| Situation | Option | Who commits/rolls back | How to get the connection |
|---|---|---|---|
| Transaction spanning several repos/services, or mixed with JPA/`JdbcTemplate` | A (`@Transactional`) | Spring | `DataSourceUtils.getConnection` |
| Transaction contained in one method, no Spring tx manager | B (`transaction(...)`) | JOrm | `dataSource.getConnection()` |
| Read-only | C | nobody (autocommit) | `dataSource.getConnection()` |

The one rule that never changes: **one `Connection` per operation/thread, taken from the pool** (Spring gives you HikariCP by default). Never share a `Runner` or a `Connection` across threads.

### AWS Lambda / Serverless

Serverless changes the trade-offs. Each Lambda execution environment handles **one request at a time**, and many short-lived containers can hammer the database with connections simultaneously. The goals are: minimize cold-start cost and avoid exhausting database connections.

Recommendations:

- **Keep the pool tiny** — a Lambda container serves one request at a time, so `maximumPoolSize = 1` (at most 2) is usually right. A large pool per container multiplied by hundreds of concurrent containers will exhaust the database.
- **Initialize the pool/connection outside the handler** so it is reused across warm invocations instead of being recreated on every request.
- **Prefer a proxy for scale** — if you run many concurrent Lambdas, put **Amazon RDS Proxy** (or your provider's equivalent) in front of the database to pool and multiplex connections centrally.

```java
public class Handler implements RequestHandler<Request, Response> {

    // Created once per container (cold start), reused on warm invocations.
    private static final HikariDataSource DATA_SOURCE = buildDataSource();

    private static HikariDataSource buildDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv("DB_URL"));
        config.setUsername(System.getenv("DB_USER"));
        config.setPassword(System.getenv("DB_PASS"));
        config.setMaximumPoolSize(1);   // one request per container at a time
        config.setConnectionTimeout(2000);
        return new HikariDataSource(config);
    }

    @Override
    public Response handleRequest(Request request, Context context) {
        try (Connection conn = DATA_SOURCE.getConnection()) {
            List<UserDao> users = new Runner<UserDao>(conn)
                    .select(buildSelector(request), UserDao.class);
            return Response.ok(users);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
```

JOrm's near-zero startup cost (no reflection scanning at boot, no proxy generation, no context initialization) is what makes this pattern viable in cold-start-sensitive environments — the only meaningful setup cost is the connection/pool itself.

---

## Relationships (OneToMany / ManyToOne)

JOrm does not provide `@OneToMany` or `@ManyToOne` annotations. This is a deliberate design decision.

Relationship loading introduces:
- Implicit N+1 queries
- Opaque lazy/eager loading behavior
- Complex lifecycle management

Instead, JOrm encourages you to model relationships **explicitly** with joins and manual or auto mapping:

```java
// Fetch users with their address in a single query
Selector selector = new Selector()
        .select("users as u",
                "u.id", "u.name", "u.email",
                "a.street", "a.city", "a.postalCode")
        .join(Join.LEFT, "userAddress as ua", "u.id = ua.userId")
        .join(Join.LEFT, "addresses as a",    "a.id = ua.addressId")
        .where("u.id = :id", p -> p.put("id", userId));

List<UserWithAddress> result = new Runner<UserWithAddress>(connection)
        .select(selector, rs -> new UserWithAddress(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("street"),
                rs.getString("city")
        ));
```

For collections (one user → many orders), fetch them separately and compose in your service layer:

```java
List<UserDao> users = runner.select(userSelector, UserDao.class);
List<OrderDao> orders = runner.select(orderSelector, OrderDao.class);

// compose in your application code
Map<Integer, List<OrderDao>> ordersByUser = orders.stream()
        .collect(Collectors.groupingBy(OrderDao::getUserId));
```

This keeps queries visible, predictable, and easy to optimize.

---

## Metrics

JOrm includes a built-in performance monitoring system. Enable it per query with `enableMetrics(alias)`:

```java
new Runner<UserDao>(connection)
        .enableMetrics("get_users")
        .select(selector, UserDao.class);
```

Metrics are stored **globally as a singleton** — shared across all `Runner` instances.

### Slow Query Detection

Configure the global threshold (default: 200ms):

```java
Metrics.setSlowThreshold(300); // queries over 300ms are marked as slow
```

### Accessing Metrics

```java
// Metrics for a specific alias
Metrics m = JormLogger.getMetrics("get_users");
long avg = m.getAverage();                     // average duration in ms
List<TimeRecord> slow = m.getSlowQueries();    // slow query records

// All slow queries across all aliases
List<TimeRecord> allSlow = JormLogger.getAllSlowQueries();

// Full metrics map
Map<String, Metrics> all = JormLogger.getMetrics();
```

### Debug Endpoint

`getMetricsSummary()` returns a JSON string ready to be served from a debug endpoint:

```java
// Spring
@GetMapping("/debug/metrics")
public ResponseEntity<String> metrics() {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(JormLogger.getMetricsSummary());
}
```

Example response:

```json
{
  "slowThresholdMs": 200,
  "queries": {
    "get_users": {
      "avgMs": 12,
      "slowCount": 0,
      "slowQueries": []
    },
    "get_orders": {
      "avgMs": 340,
      "slowCount": 3,
      "slowQueries": [
        { "sql": "SELECT ...", "durationMs": 412 },
        { "sql": "SELECT ...", "durationMs": 387 },
        { "sql": "SELECT ...", "durationMs": 298 }
      ]
    }
  }
}
```

### Flush

Clear all accumulated metrics (useful between test runs or on demand):

```java
JormLogger.flush();
```

---

## Debug Logging

Enable query logging for a specific runner:

```java
List<UserDao> users = new Runner<UserDao>(connection)
        .enableLogs()
        .select(selector, UserDao.class);
```

---

## Caching

JOrm does not include a built-in cache layer. This is intentional — caching strategy depends heavily on your infrastructure, consistency requirements, and data access patterns. A generic cache baked into an ORM tends to create more problems than it solves.

For applications that need query result caching, the recommended approach is to handle it at the service layer using a dedicated cache like **Redis**:

```java
public List<UserDao> getUsers(String role) {
    String cacheKey = "users:role:" + role;

    // try cache first
    List<UserDao> cached = redis.get(cacheKey, List.class);
    if (cached != null) return cached;

    // miss — query the database
    List<UserDao> users = new Runner<UserDao>(connection)
            .select(selector, UserDao.class);

    redis.set(cacheKey, users, Duration.ofMinutes(5));
    return users;
}
```

This approach gives you full control over:
- **TTL** per query type
- **Cache invalidation** on write operations
- **Consistency model** (cache-aside, write-through, etc.)
- **Serialization format** (JSON, MessagePack, etc.)

JOrm's [Metrics](#metrics) can help you identify which queries are worth caching — start with the ones showing high average duration or frequent slow query flags.

---

## Why JOrm?

JOrm does not try to abstract SQL away. It gives you:

- A **fluent query builder** that composes cleanly
- **Automatic entity mapping** when you want convenience
- **Manual mapping** when you need control
- **Built-in metrics** without any extra dependency
- A **near-zero startup cost** — no reflection scanning, no proxy generation, no context initialization

Because of its **< 80KB footprint** and **single lightweight dependency**, JOrm is especially well-suited for:

- AWS Lambda and other FaaS platforms where cold start time matters
- Microservices that need a small, auditable dependency tree
- High-throughput APIs where ORM overhead is unacceptable

---
## Testing

JOrm includes integration tests powered by [Testcontainers](https://testcontainers.com/).

The entire test suite lives in a single dialect-agnostic class (`AbstractDialectTest`) and
runs **unchanged against all three supported databases**: PostgreSQL, MySQL and Oracle. Each
dialect (`PostgresTest`, `MysqlTest`, `OracleTest`) simply provides its own connection and
mock schema — the actual test cases (SELECT, pagination, WHERE IN, insert/upsert, batch,
update, soft/hard delete and transactions) are shared.

Running the same assertions across three real database engines makes the library
significantly more robust: any dialect-specific SQL generation issue, mapping mismatch or
transactional edge case surfaces immediately, because the expected behavior is verified
identically on every engine.

Each suite spins up a real database inside a Docker container via Testcontainers, so queries,
mappings, and transactions are validated against an actual database engine — not a mock or an
in-memory substitute. This guarantees that behavior in tests matches real-world environments
without requiring developers to install or configure PostgreSQL, MySQL or Oracle locally.

To keep the suite fast, each dialect starts its container **once** and shares it across all
test methods, rather than booting a fresh container per test.
---
## Why JOrm Exists

JOrm was created after working on large enterprise systems where SQL queries were duplicated across many services.

Adding a new column often required searching and modifying dozens of queries across the codebase.

Traditional ORMs like Hibernate were not a good fit for those environments due to their complexity and heavy dependencies.

JOrm was designed as a lightweight alternative that allows developers to keep full control over SQL while reducing duplication and improving maintainability.
---

## License

MIT License

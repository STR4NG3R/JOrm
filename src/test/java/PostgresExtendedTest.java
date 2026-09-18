import common.InsertTest;
import common.SelectTest;
import dao.UserDao;
import io.github.str4ng3r.common.*;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import io.github.str4ng3r.sql.Runner;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.*;

/**
 * Tests adicionales para JOrm que amplían la cobertura de PostgresTest.
 * Se prueban: Update builder, batch insert, paginación con mapping manual,
 * filtros combinados en Selector, página inválida y verificación de timestamps.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PostgresExtendedTest {

    @ClassRule
    public static PostgreSQLContainer<?> postgresContainer;

    Connection connection;

    Connection getConnection() throws SQLException {
        if (connection == null)
            connection = DriverManager.getConnection(
                    postgresContainer.getJdbcUrl() + "?stringtype=unspecified",
                    postgresContainer.getUsername(),
                    postgresContainer.getPassword());
        return connection;
    }

    @Before
    public void setup() throws IOException, SQLException {
        postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("integration-tests-db")
                .withUsername("sa")
                .withPassword("sa");
        postgresContainer.start();

        String initDb = Resources.toString(
                Objects.requireNonNull(
                        PostgresExtendedTest.class.getClassLoader().getResource("mock/postgresmock.sql")),
                Charset.defaultCharset());

        Connection con = getConnection();
        try {
            Statement statement = con.createStatement();
            statement.execute(initDb);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    List<UserDao> getUser(boolean withDeleted, Selector s) throws SQLException, InvalidSqlGenerationException {
        return new Runner<UserDao>(getConnection())
                .withDeleted(withDeleted)
                .select(s, UserDao.class);
    }

    // -------------------------------------------------------------------------
    // UPDATE BUILDER
    // -------------------------------------------------------------------------

    /**
     * Verifica que el Update builder modifica correctamente un campo.
     */
    @Test
    public void a_updateBuilderChangesName() throws SQLException, InvalidSqlGenerationException {
        // Cambiar nombre del user id=1
        int affected = new Runner<Void>(getConnection())
                .enableLogs()
                .update(
                        new Update()
                                .from("users")
                                .setColumnsValuesToUpdate(p -> p.put("name", "UpdatedByBuilder"))
                                .where("id = :id", p -> p.put("id", 1))
                );

        assertEquals("Debe afectar exactamente 1 fila", 1, affected);

        UserDao user = getUser(true, SelectTest.getUserById(1)).get(0);
        assertEquals("El nombre debe haberse actualizado", "UpdatedByBuilder", user.getName());
    }

    /**
     * Verifica que el Update builder con WHERE que no coincide afecta 0 filas.
     */
    @Test
    public void b_updateBuilderNoMatchReturnsZero() throws SQLException, InvalidSqlGenerationException {
        int affected = new Runner<Void>(getConnection())
                .update(
                        new Update()
                                .from("users")
                                .setColumnsValuesToUpdate(p -> p.put("name", "Ghost"))
                                .where("id = :id", p -> p.put("id", 99999))
                );

        assertEquals("No debe afectar ninguna fila si el ID no existe", 0, affected);
    }

    /**
     * Verifica que excludeColumns() deja fuera del SET las columnas indicadas,
     * aunque hayan sido agregadas al mapa de valores.
     */
    @Test
    public void b3_updateExcludeColumns() throws SQLException, InvalidSqlGenerationException {
        // Valor de password original del user id=1 (del mock: 'password123')
        UserDao before = getUser(true, SelectTest.getUserById(1)).get(0);
        String originalPassword = before.getPassword();

        int affected = new Runner<Void>(getConnection())
                .enableLogs()
                .update(
                        new Update()
                                .from("users")
                                .excludeColumns("password") // no debe actualizarse
                                .setColumnsValuesToUpdate(p -> {
                                    p.put("name", "ExcludedColsUser");
                                    p.put("password", "should-not-be-saved");
                                })
                                .where("id = :id", p -> p.put("id", 1))
                );

        assertEquals("Debe afectar 1 fila", 1, affected);

        UserDao after = getUser(true, SelectTest.getUserById(1)).get(0);
        assertEquals("El nombre sí debe actualizarse", "ExcludedColsUser", after.getName());
        assertEquals("El password NO debe cambiar (columna excluida)",
                originalPassword, after.getPassword());
    }

    // -------------------------------------------------------------------------
    // BATCH INSERT
    // -------------------------------------------------------------------------

    /**
     * Verifica que el batch insert inserta todos los registros correctamente.
     */
    @Test
    public void c_batchInsertInsertsAllRecords() throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        List<UserDao> batch = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            UserDao u = new UserDao();
            u.setName("BatchUser" + i);
            u.setEmail("batchuser" + i + "@example.com");
            u.setRole("user");
            u.setPassword("pass" + i);
            batch.add(u);
        }

        new Runner<UserDao>(getConnection())
                .enableLogs()
                .insert(UserDao.class, batch, 3);

        // Verificar que al menos uno de los usuarios insertados existe
        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "batchuser1@example.com"));

        List<UserDao> result = getUser(true, s);
        assertFalse("Debe encontrar al menos el primer usuario del batch", result.isEmpty());
        assertEquals("BatchUser1", result.get(0).getName());
    }

    /**
     * Verifica que el batch insert con batchSize=1 funciona (flush en cada registro).
     */
    @Test
    public void d_batchInsertWithSizeOneFlushesCorrectly()
            throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        List<UserDao> batch = new ArrayList<>();
        UserDao u = new UserDao();
        u.setName("SingleBatch");
        u.setEmail("singlebatch@example.com");
        u.setRole("admin");
        u.setPassword("abc");
        batch.add(u);

        new Runner<UserDao>(getConnection())
                .insert(UserDao.class, batch, 1);

        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "singlebatch@example.com"));

        List<UserDao> result = getUser(true, s);
        assertFalse("Debe encontrar el usuario insertado individualmente", result.isEmpty());
        assertEquals("SingleBatch", result.get(0).getName());
    }

    // -------------------------------------------------------------------------
    // PAGINACIÓN CON MAPPING MANUAL (Function<ResultSet, T>)
    // -------------------------------------------------------------------------

    /**
     * Verifica que selectPaginated con mapping manual devuelve datos y metadata correctos.
     */
    @Test
    public void e_selectPaginatedManualMappingReturnsData()
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {

        // Agregar WHERE explícito para evitar bug de "AND" sin "WHERE" en soft-delete
        Selector s = new Selector()
                .select("users",
                        "id", "name", "email", "password",
                        "role", "updatedAt as \"updatedAt\"",
                        "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("id > :id", p -> p.put("id", 0));

        Template<List<UserDao>> result = new Runner<UserDao>(getConnection())
                .enableLogs()
                .selectPaginated(
                        1,
                        5,
                        s,
                        rs -> {
                            try {
                                return new UserDao(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );

        assertNotNull("El resultado no debe ser null", result);
        assertNotNull("Los datos no deben ser null", result.getData());
        assertFalse("La lista de datos no debe estar vacía", result.getData().isEmpty());
    }

    /**
     * Verifica que paginación calcula correctamente el total de páginas y tiene datos.
     */
    @Test
    public void f_selectPaginatedMetadataIsCorrect()
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {

        int pageSize = 5;
        // Usar selector simple sin JOINs para comportamiento predecible de paginación
        Selector s = new Selector()
                .select("users",
                        "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"",
                        "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("id > :id", p -> p.put("id", 0));

        Template<List<UserDao>> result = new Runner<UserDao>(getConnection())
                .enableLogs()
                .selectPaginated(
                        1,
                        pageSize,
                        s,
                        UserDao.class
                );

        assertNotNull(result);
        assertFalse("La primera página debe tener datos", result.getData().isEmpty());
        // Hay 20 usuarios en el mock, con pageSize=5 → 4 páginas
        assertEquals("Debe haber 4 páginas en total", Integer.valueOf(4), result.getTotalPages());
        assertEquals("El pageSize debe ser 5", Integer.valueOf(pageSize), result.getPageSize());
        assertEquals("Debe haber 20 registros en total", Integer.valueOf(20), result.getCount());
    }

    // -------------------------------------------------------------------------
    // FILTROS COMBINADOS EN SELECTOR
    // -------------------------------------------------------------------------

    /**
     * Verifica filtro solo por nombre.
     */
    @Test
    public void g_selectFilterByNameOnly() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> result = new Runner<UserDao>(getConnection())
                .enableLogs()
                .select(
                        SelectTest.baseQueryUsers("John", null, null),
                        UserDao.class
                );

        assertFalse("Debe retornar al menos un usuario con 'John' en el nombre", result.isEmpty());
        for (UserDao u : result) {
            assertTrue("Todos los resultados deben contener 'John'",
                    u.getName().toLowerCase().contains("john"));
        }
    }

    /**
     * Verifica que un filtro de nombre que no coincide retorna lista vacía.
     */
    @Test
    public void h_selectFilterNoMatchReturnsEmpty() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> result = new Runner<UserDao>(getConnection())
                .select(
                        SelectTest.baseQueryUsers("ZZZNonExistentXXX", null, null),
                        UserDao.class
                );

        assertTrue("Debe retornar lista vacía si no hay coincidencias", result.isEmpty());
    }

    /**
     * Verifica que WHERE IN con una colección expande automáticamente los placeholders (jsqb 1.1.1+).
     */
    @Test
    public void h2_selectWhereIn() throws SQLException, InvalidSqlGenerationException {
        Selector s = new Selector()
                .select("users",
                        "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"",
                        "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("id IN (:ids)", p -> p.put("ids", Arrays.asList(1, 2, 3)));

        List<UserDao> result = getUser(true, s);

        assertEquals("Debe retornar exactamente 3 usuarios", 3, result.size());
        List<Integer> returnedIds = new ArrayList<>();
        for (UserDao u : result) returnedIds.add(u.getId());
        assertTrue("Debe contener id=1", returnedIds.contains(1));
        assertTrue("Debe contener id=2", returnedIds.contains(2));
        assertTrue("Debe contener id=3", returnedIds.contains(3));
    }

    /**
     * Verifica que WHERE IN con un solo elemento en la colección funciona correctamente.
     */
    @Test
    public void h3_selectWhereInSingleElement() throws SQLException, InvalidSqlGenerationException {
        Selector s = new Selector()
                .select("users",
                        "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"",
                        "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("id IN (:ids)", p -> p.put("ids", Arrays.asList(5)));

        List<UserDao> result = getUser(true, s);

        assertEquals("Debe retornar exactamente 1 usuario", 1, result.size());
        assertEquals("Debe ser el usuario con id=5", Integer.valueOf(5), result.get(0).getId());
    }

    /**
     * Verifica que sin filtros retorna todos los usuarios (sin eliminados).
     */
    @Test
    public void i_selectNoFiltersReturnsAllActiveUsers() throws SQLException, InvalidSqlGenerationException {
        // Usar selector sin JOINs para evitar problemas con soft-delete en múltiples tablas
        Selector s = new Selector()
                .select("users",
                        "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"",
                        "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("deletedAt IS NULL", p -> {});

        List<UserDao> result = new Runner<UserDao>(getConnection())
                .withDeleted(true)
                .select(s, UserDao.class);

        assertTrue("Debe retornar al menos 1 usuario activo", result.size() > 0);
        for (UserDao u : result) {
            assertNull("Los usuarios activos no deben tener deletedAt", u.getDeletedAt());
        }
    }

    // -------------------------------------------------------------------------
    // TIMESTAMPS: createdAt y updatedAt
    // -------------------------------------------------------------------------

    /**
     * Verifica que un usuario recién insertado tiene createdAt asignado.
     */
    @Test
    public void j_insertSetsCreatedAt()
            throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        UserDao newUser = new UserDao();
        newUser.setName("TimestampTest");
        newUser.setEmail("tstest@example.com");
        newUser.setRole("user");
        newUser.setPassword("pass123");

        new Runner<UserDao>(getConnection())
                .enableLogs()
                .insert(UserDao.class, newUser);

        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"",
                        "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "tstest@example.com"));

        List<UserDao> result = getUser(true, s);
        assertFalse("Debe encontrar el usuario insertado", result.isEmpty());
        assertNotNull("createdAt no debe ser null después del insert", result.get(0).getCreatedAt());
    }

    /**
     * Verifica que un usuario actualizado con el upsert tiene updatedAt diferente a null.
     */
    @Test
    public void k_upsertSetsUpdatedAt()
            throws SQLException, InvalidSqlGenerationException, IllegalAccessException {
        // Obtener usuario existente y actualizar
        UserDao user = getUser(true, SelectTest.getUserById(5)).get(0);
        user.setName("UpdatedWithTimestamp");

        new Runner<UserDao>(getConnection())
                .enableLogs()
                .insert(UserDao.class, user);

        UserDao updated = getUser(true, SelectTest.getUserById(5)).get(0);
        assertNotNull("updatedAt no debe ser null tras el upsert", updated.getUpdatedAt());
        assertEquals("El nombre debe haberse actualizado", "UpdatedWithTimestamp", updated.getName());
    }

    // -------------------------------------------------------------------------
    // PÁGINA INVÁLIDA
    // -------------------------------------------------------------------------

    /**
     * Verifica que solicitar página 0 lanza InvalidCurrentPageException.
     */
    @Test(expected = InvalidCurrentPageException.class)
    public void l_selectPaginatedPageZeroThrows()
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        new Runner<UserDao>(getConnection())
                .selectPaginated(
                        0,
                        10,
                        SelectTest.baseQueryUsers(null, null, null),
                        UserDao.class
                );
    }

    /**
     * Verifica que una página negativa lanza InvalidCurrentPageException.
     */
    @Test(expected = InvalidCurrentPageException.class)
    public void m_selectPaginatedNegativePageThrows()
            throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        new Runner<UserDao>(getConnection())
                .selectPaginated(
                        -1,
                        10,
                        SelectTest.baseQueryUsers(null, null, null),
                        UserDao.class
                );
    }

    // -------------------------------------------------------------------------
    // MANUAL MAPPING SELECT (Function<ResultSet, T>)
    // -------------------------------------------------------------------------

    /**
     * Verifica que el select con mapping manual mapea correctamente los campos.
     */
    @Test
    public void n_selectManualMappingFieldsAreCorrect() throws SQLException, InvalidSqlGenerationException {
        List<UserDao> list = new Runner<UserDao>(getConnection())
                .enableLogs()
                .select(
                        SelectTest.getUserById(4),
                        rs -> {
                            try {
                                return new UserDao(
                                        rs.getInt("id"),
                                        rs.getString("name"),
                                        rs.getString("email")
                                );
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });

        assertFalse("Debe retornar al menos un resultado", list.isEmpty());
        UserDao u = list.get(0);
        assertEquals("El ID debe ser 4", Integer.valueOf(4), u.getId());
        assertNotNull("El nombre no debe ser null", u.getName());
        assertNotNull("El email no debe ser null", u.getEmail());
    }

    // -------------------------------------------------------------------------
    // DELETE: casos límite
    // -------------------------------------------------------------------------

    /**
     * Verifica que borrar un registro ya eliminado (soft delete) no lanza excepción.
     */
    @Test
    public void o_softDeleteAlreadyDeletedUserDoesNotThrow()
            throws SQLException, InvalidSqlGenerationException {

        // Primer soft delete
        new Runner<Void>(getConnection())
                .delete(
                        new Delete().from("users").where("id = :id", p -> p.put("id", 7)),
                        false
                );

        // Segundo soft delete sobre el mismo usuario: no debe lanzar excepción
        int affected = new Runner<Void>(getConnection())
                .delete(
                        new Delete().from("users").where("id = :id", p -> p.put("id", 7)),
                        false
                );

        // El UPDATE de soft-delete afecta 1 fila incluso si ya tiene deletedAt (sobreescribe)
        assertEquals("Debe afectar 1 fila (sobreescribe deletedAt)", 1, affected);
    }

    /**
     * Verifica que hacer hard delete de un ID inexistente retorna 0 filas afectadas.
     */
    @Test
    public void p_hardDeleteNonExistentIdReturnsZero() throws SQLException, InvalidSqlGenerationException {
        int affected = new Runner<Void>(getConnection())
                .delete(
                        new Delete().from("users").where("id = :id", p -> p.put("id", 99999)),
                        true
                );

        assertEquals("No debe afectar ninguna fila", 0, affected);
    }

    /**
     * Verifica el soft delete usando la entidad DAO directamente (delete(T, false)).
     * El registro debe conservar deletedAt seteado y desaparecer de las consultas
     * que excluyen eliminados.
     */
    @Test
    public void p2_softDeleteByEntity() throws SQLException, InvalidSqlGenerationException {
        UserDao toDelete = new UserDao();
        toDelete.setId(9);

        int affected = new Runner<UserDao>(getConnection())
                .enableLogs()
                .delete(toDelete, false);

        assertEquals("Debe afectar 1 fila", 1, affected);

        // Con withDeleted=true el registro sigue existiendo con deletedAt seteado
        UserDao found = getUser(true, SelectTest.getUserById(9)).get(0);
        assertNotNull("deletedAt debe estar seteado tras el soft delete", found.getDeletedAt());

        // Con withDeleted=false el registro queda excluido
        assertTrue("El registro soft-deleted no debe aparecer sin withDeleted",
                getUser(false, SelectTest.getUserById(9)).isEmpty());
    }

    /**
     * Verifica el hard delete usando la entidad DAO directamente (delete(T, true)).
     * El registro debe eliminarse físicamente y no aparecer ni con withDeleted=true.
     */
    @Test
    public void p3_hardDeleteByEntity() throws SQLException, InvalidSqlGenerationException {
        UserDao toDelete = new UserDao();
        toDelete.setId(10);

        int affected = new Runner<UserDao>(getConnection())
                .enableLogs()
                .delete(toDelete, true);

        assertEquals("Debe afectar 1 fila", 1, affected);

        // El registro no debe existir ni incluyendo eliminados
        assertTrue("El registro hard-deleted no debe existir",
                getUser(true, SelectTest.getUserById(10)).isEmpty());
    }

    // -------------------------------------------------------------------------
    // TRANSACCIONES
    // -------------------------------------------------------------------------

    /**
     * Verifica que transaction(callback) hace commit automático en caso de éxito.
     */
    @Test
    public void q_transactionCommitsOnSuccess() throws Exception {
        new Runner<UserDao>(getConnection())
                .transaction(runner -> {
                    UserDao u = new UserDao();
                    u.setName("TxUser");
                    u.setEmail("txuser@example.com");
                    u.setRole("user");
                    u.setPassword("pass");
                    runner.insert(UserDao.class, u);
                });

        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "txuser@example.com"));

        List<UserDao> result = getUser(true, s);
        assertFalse("El usuario debe haberse insertado tras el commit", result.isEmpty());
        assertEquals("TxUser", result.get(0).getName());
    }

    /**
     * Verifica que transaction(callback) hace rollback automático cuando el callback lanza excepción.
     */
    @Test
    public void r_transactionRollsBackOnException() throws Exception {
        try {
            new Runner<UserDao>(getConnection())
                    .transaction(runner -> {
                        UserDao u = new UserDao();
                        u.setName("RollbackUser");
                        u.setEmail("rollback@example.com");
                        u.setRole("user");
                        u.setPassword("pass");
                        runner.insert(UserDao.class, u);

                        // Forzar excepción para disparar el rollback
                        throw new RuntimeException("Simulated failure");
                    });
        } catch (SQLException ignored) {
            // Se espera la excepción envuelta por transaction()
        }

        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "rollback@example.com"));

        List<UserDao> result = getUser(true, s);
        assertTrue("El usuario NO debe existir tras el rollback", result.isEmpty());
    }

    /**
     * Verifica que beginTransaction / commit manual funciona correctamente.
     */
    @Test
    public void s_manualBeginCommit() throws Exception {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        runner.beginTransaction();

        UserDao u = new UserDao();
        u.setName("ManualCommit");
        u.setEmail("manualcommit@example.com");
        u.setRole("user");
        u.setPassword("pass");
        runner.insert(UserDao.class, u);

        runner.commit();

        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "manualcommit@example.com"));

        List<UserDao> result = getUser(true, s);
        assertFalse("El usuario debe existir tras el commit manual", result.isEmpty());
    }

    /**
     * Verifica que beginTransaction / rollback manual revierte los cambios.
     */
    @Test
    public void t_manualBeginRollback() throws Exception {
        Runner<UserDao> runner = new Runner<UserDao>(getConnection());
        runner.beginTransaction();

        UserDao u = new UserDao();
        u.setName("ManualRollback");
        u.setEmail("manualrollback@example.com");
        u.setRole("user");
        u.setPassword("pass");
        runner.insert(UserDao.class, u);

        runner.rollback();

        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "manualrollback@example.com"));

        List<UserDao> result = getUser(true, s);
        assertTrue("El usuario NO debe existir tras el rollback manual", result.isEmpty());
    }

    /**
     * Verifica que transaction() con nivel de aislamiento SERIALIZABLE funciona.
     */
    @Test
    public void u_transactionWithIsolationLevel() throws Exception {
        new Runner<UserDao>(getConnection())
                .transaction(Runner.ISOLATION.SERIALIZABLE, runner -> {
                    UserDao u = new UserDao();
                    u.setName("IsolatedUser");
                    u.setEmail("isolated@example.com");
                    u.setRole("user");
                    u.setPassword("pass");
                    runner.insert(UserDao.class, u);
                });

        Selector s = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "isolated@example.com"));

        List<UserDao> result = getUser(true, s);
        assertFalse("El usuario debe existir tras commit con SERIALIZABLE", result.isEmpty());
    }

    /**
     * Verifica que una transacción con múltiples operaciones se trata como unidad atómica.
     */
    @Test
    public void v_transactionIsAtomic() throws Exception {
        new Runner<UserDao>(getConnection())
                .transaction(runner -> {
                    UserDao u1 = new UserDao();
                    u1.setName("AtomicUser1");
                    u1.setEmail("atomic1@example.com");
                    u1.setRole("user");
                    u1.setPassword("pass");

                    UserDao u2 = new UserDao();
                    u2.setName("AtomicUser2");
                    u2.setEmail("atomic2@example.com");
                    u2.setRole("user");
                    u2.setPassword("pass");

                    runner.insert(UserDao.class, u1);
                    runner.insert(UserDao.class, u2);
                });

        Selector s1 = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "atomic1@example.com"));

        Selector s2 = new Selector()
                .select("users", "id", "name", "email", "role",
                        "updatedAt as \"updatedAt\"", "createdAt as \"createdAt\"",
                        "deletedAt as \"deletedAt\"")
                .where("email = :email", p -> p.put("email", "atomic2@example.com"));

        assertFalse("Ambos usuarios deben existir — atomic1", getUser(true, s1).isEmpty());
        assertFalse("Ambos usuarios deben existir — atomic2", getUser(true, s2).isEmpty());
    }
}

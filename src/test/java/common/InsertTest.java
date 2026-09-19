package common;

import dao.UserDao;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable entity factories for insert/upsert/batch tests.
 */
public class InsertTest {

    public static UserDao generateUser() {
        UserDao user = new UserDao();
        user.setEmail("heyamail@email.com");
        user.setRole("Admin");
        user.setName("Pablo");
        user.setPassword("sdfasdf");
        return user;
    }

    public static UserDao duplicatedUserUpdate() {
        UserDao user = new UserDao();
        user.setId(1);
        user.setName("Duplicated user");
        return user;
    }

    /**
     * Builds a user with a caller-provided unique email, so the same test can run
     * against different databases without unique-constraint collisions.
     */
    public static UserDao newUser(String name, String email) {
        UserDao user = new UserDao();
        user.setName(name);
        user.setEmail(email);
        user.setRole("user");
        user.setPassword("secret");
        return user;
    }

    /**
     * Builds a list of users with sequential unique emails for batch insert tests.
     */
    public static List<UserDao> userBatch(String prefix, int count) {
        List<UserDao> batch = new ArrayList<>();
        for (int i = 1; i <= count; i++)
            batch.add(newUser(prefix + i, prefix.toLowerCase() + i + "@example.com"));
        return batch;
    }
}

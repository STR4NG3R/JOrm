package common;

import dao.UserDao;

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
}

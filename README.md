JOrm
=================

JOrm is a simple java Orm with capabilities to use entity like frameworks
Its built from scratch no dependencies, and it is focused
to be simple and can be used on any kind of project, since it only needs the 
connection to work, it has powerful features such as query builder,
partial updates, insert, delete and mapper.
This project uses QueryBuilder4J for the Sql Generation

You could look at the unit test cases to get some inspiration.
<a name="index_block"></a>

* [1. Installation](#block1)
* [1.1. Installation with Maven](#block1.1)
* [2. SELECT Statement](#block2)
    * [2.1. Basic SELECT statement](#block2.1)
    * [2.2. SELECT with Specific Fields statement](#block2.2)
    * [2.3. SELECT with pagination](#block2.2)
* [3. INNER JOIN statement](#block3)
    * [3.1 Simple Inner join](#block3.1)
* [4. Insert Statement](#block4)
  * [4.1 Simple Insert](#block4.1)
* [5. Update Statement](#block5)
  * [5.1 Simple Update](#block5.1)
* [6. Delete Statement](#block6)
  * [6.1 Simple Delete](#block6.1)
* [7. Entity Declaration](#block6)
* [8. Author](#block7)
* [9. License](#block8)

<a name="block1"></a>
## 1. Installation [↑](#index_block)
For default installation, see [Releases](https://github.com/derickfelix/jsqb/releases) section to download the .jar file and add it to the path of your project.
<a name="block1.1"></a>
### 1.1. Installation with Maven [↑](#index_block)
To install with maven

[![](https://jitpack.io/v/STR4NG3R/JOrm.svg)](https://jitpack.io/#STR4NG3R/JOrm)

Step 1. Add the JitPack repository to your build file
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Step 2. Add the dependency
```xml
<dependency>
   <groupId>com.github.STR4NG3R</groupId>
   <artifactId>JOrm</artifactId>
   <version>Tag</version>
</dependency>
```


<a name="block2"></a>
## 2. SELECT Statement [↑](#index_block)

<a name="block2.1"></a>
### 2.1. Basic SELECT statement [↑](#index_block)
#### Usage:
```java
public class Usage {
    public static void main(String[] args)
    {
      UserDao user = new Runner<UserDao>()
              .setConnection(getConnection())
              .select(
                      new Selector()
                              .select("users", "id", "name", "email", "password", "role")
                              .where("id = :id", (p) -> p.put("id", 1)),
                      UserDao.class
              ).get(0);
    }
}
```

<a name="block2.2"></a>
### 2.2. SELECT with Specific Fields [↑](#index_block)
#### Usage:
```java
public class Usage {
    public static void main(String[] args)
    {
      String name = "Pablo", lastName = null, cp = null;
      Selector s = new Selector()
              .select("users as u",
                      "u.id id", "u.name name", "u.email email", "u.role role",
                      "u.email as email"
              )
              .join(Join.JOIN.LEFT, "userAddress as ua", "u.id = ua.userId")
              .join(Join.JOIN.INNER, "addresses as a", "a.id = ua.addressId")
              .setDialect(Constants.SqlDialect.Postgres);

      if (name != null)
        s.andWhere("u.name LIKE  CONCAT('%', :name, '%')", parameters -> parameters.put("name", name));

      if (lastName != null)
        s.andWhere("u.lastName LIKE CONCAT('%', :lastName, '%')", parameters -> parameters.put("lastName", lastName));

      if (cp != null)
        s.andWhere("a.cp = :cp", parameters -> parameters.put("cp", cp));
      
      UserDao user = new Runner<UserDao>()
              .setConnection(getConnection())
              .select(s, UserDao.class)
              .get(0); 
    }
}
```

<a name="block2.3"></a>
### 2.3. SELECT with Pagination [↑](#index_block)
You can paginate your database as simple like below code
#### Usage:
```java
public class Usage {
    public static void main(String[] args)
    {
      String name = "Pablo", lastName = null, cp = null;
      Selector s = new Selector()
              .select("users as u",
                      "u.id id", "u.name name", "u.email email", "u.role role",
                      "u.email as email"
              )
              .join(Join.JOIN.LEFT, "userAddress as ua", "u.id = ua.userId")
              .join(Join.JOIN.INNER, "addresses as a", "a.id = ua.addressId")
              .setDialect(Constants.SqlDialect.Postgres);

      if (name != null)
        s.andWhere("u.name LIKE  CONCAT('%', :name, '%')", parameters -> parameters.put("name", name));

      if (lastName != null)
        s.andWhere("u.lastName LIKE CONCAT('%', :lastName, '%')", parameters -> parameters.put("lastName", lastName));

      if (cp != null)
        s.andWhere("a.cp = :cp", parameters -> parameters.put("cp", cp));

      Template<List<UserDao>> paginated = new Runner<UserDao>()
              .setConnection(getConnection())
              .selectPaginated(
                      1,
                      10,
                      s,
                      UserDao.class
              );
      // This generate a paginated list with the first 10 elements
    }
}
```

<a name="block3"></a>
## 3. INNER JOIN statement [↑](#index_block)

<a name="block3.1"></a>
### 3.1. Simple Inner join [↑](#index_block)
The `join()` method expects an enum of possible type of JOIN
This method is described as:
`innerJoin(JOIN join,String table, String on)`.

#### Usage:
```java
public class Usage {
    public static void main(String[] args)
    {
      List<UserDao> user = new Runner<UserDao>()
              .setConnection(getConnection())
              .select(
                      new Selector()
                              .select("users as u", "id", "name", "email")
                              .join(JOIN.INNER, "roles as r", "r.id = u.role_id")
                              .addSelect("r.name", "r.id", "r.level")
                              .join(JOIN.RIGHT, "address as a", "a.user_id = u.id")
                              .addSelect("a.street", "a.cp", "a.number")
                              .write(),
                      UserDao.class
              );
    
    }
}
```

<a name="block4"></a>
### 4 Insert Statement[↑](#index_block)
If you pass an already inserted id record it shoudl partially update
<a name="block4.1"></a>
### 4.1 Simple Insert Example[↑](#index_block)
#### Usage:
```java
public class Usage {
  public static void main(String[] args)
  {

    new Runner<UserDao>()
            .setConnection(getConnection())
            .insert(
                    UserDao.class, 
                    new UserDao("Pablo", "anemail@email.com", "password123", "customer")
            );
  }
}
```

<a name="block5"></a>
### 5 Update Statement[↑](#index_block)
Same as insert, but you can exclude columns to be updated

<a name="block6"></a>
### 6 Delete Statement[↑](#index_block)
Simple delete statement

<a name="block7"></a>
### 7 Entity Declaration[↑](#index_block)
Such as another orms this support entity pattern design
```java
package dao;

import org.example.Column;
import org.example.Entity;
import org.example.Id;

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
}
```

<a name="block8"></a>
## 8. Authors [↑](#index_block)
Pablo Eduardo Martinez Solis
- <pablo980629@hotmail.com>
- [https://github.com/STR4NG3R](https://github.com/STR4NG3R)


<a name="block9"></a>
## 9. License [↑](#index_block)
JOrm is licensed under the GPLv3 license.

```
The GPLv3 License (GPLv3)

Copyright (c) 2023 Pablo Eduardo Martinez Solis, Derick Felix

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

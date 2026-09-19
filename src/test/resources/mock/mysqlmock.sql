CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) DEFAULT 'user',
    createdAt TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    deletedAt TIMESTAMP NULL DEFAULT NULL
);

CREATE TABLE addresses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postalCode VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    createdAt TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE userAddress (
    userId INT,
    addressId INT,
    PRIMARY KEY (userId, addressId),
    FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (addressId) REFERENCES addresses(id) ON DELETE CASCADE
);

INSERT INTO users (name, email, password, role, createdAt, updatedAt) VALUES
('John Doe', 'john.doe@example.com', 'password123', 'admin', '2023-05-01 12:00:00', '2023-05-01 12:00:00'),
('Jane Smith', 'jane.smith@example.com', 'password123', 'user', '2023-06-15 12:01:00', '2023-06-15 12:01:00'),
('Alice Brown', 'alice.brown@example.com', 'password123', 'user', '2023-07-10 12:02:00', '2023-07-10 12:02:00'),
('Bob Johnson', 'bob.johnson@example.com', 'password123', 'guest', '2023-08-20 12:03:00', '2023-08-20 12:03:00'),
('Charlie White', 'charlie.white@example.com', 'password123', 'user', '2023-09-05 12:04:00', '2023-09-05 12:04:00'),
('Daisy Green', 'daisy.green@example.com', 'password123', 'user', '2023-10-01 12:05:00', '2023-10-01 12:05:00'),
('Edward Black', 'edward.black@example.com', 'password123', 'admin', '2023-11-10 12:06:00', '2023-11-10 12:06:00'),
('Fiona Gray', 'fiona.gray@example.com', 'password123', 'guest', '2023-12-01 12:07:00', '2023-12-01 12:07:00'),
('George Blue', 'george.blue@example.com', 'password123', 'user', '2023-12-03 12:08:00', '2023-12-03 12:08:00'),
('Hannah Yellow', 'hannah.yellow@example.com', 'password123', 'user', '2023-12-05 12:09:00', '2023-12-05 12:09:00'),
('Ian Orange', 'ian.orange@example.com', 'password123', 'user', '2023-12-07 12:10:00', '2023-12-07 12:10:00'),
('Jenny Red', 'jenny.red@example.com', 'password123', 'guest', '2023-12-09 12:11:00', '2023-12-09 12:11:00'),
('Kyle Brown', 'kyle.brown@example.com', 'password123', 'admin', '2023-11-15 12:12:00', '2023-11-15 12:12:00'),
('Lara Purple', 'lara.purple@example.com', 'password123', 'user', '2023-10-20 12:13:00', '2023-10-20 12:13:00'),
('Mike Silver', 'mike.silver@example.com', 'password123', 'user', '2023-09-25 12:14:00', '2023-09-25 12:14:00'),
('Nina Gold', 'nina.gold@example.com', 'password123', 'user', '2023-08-10 12:15:00', '2023-08-10 12:15:00'),
('Oscar Pink', 'oscar.pink@example.com', 'password123', 'guest', '2023-07-15 12:16:00', '2023-07-15 12:16:00'),
('Paula Lime', 'paula.lime@example.com', 'password123', 'user', '2023-06-01 12:17:00', '2023-06-01 12:17:00'),
('Quinn Cyan', 'quinn.cyan@example.com', 'password123', 'user', '2023-05-10 12:18:00', '2023-05-10 12:18:00'),
('Rachel Teal', 'rachel.teal@example.com', 'password123', 'user', '2023-04-20 12:19:00', '2023-04-20 12:19:00');

INSERT INTO addresses (street, city, state, postalCode, country, createdAt, updatedAt) VALUES
('123 Maple St', 'Springfield', 'IL', '62701', 'USA', '2023-05-01 12:00:00', '2023-05-01 12:00:00'),
('456 Oak St', 'Springfield', 'IL', '62701', 'USA', '2023-06-15 12:01:00', '2023-06-15 12:01:00'),
('789 Pine St', 'Chicago', 'IL', '60601', 'USA', '2023-07-10 12:02:00', '2023-07-10 12:02:00'),
('321 Birch St', 'New York', 'NY', '10001', 'USA', '2023-08-20 12:03:00', '2023-08-20 12:03:00'),
('654 Cedar St', 'Los Angeles', 'CA', '90001', 'USA', '2023-09-05 12:04:00', '2023-09-05 12:04:00'),
('987 Elm St', 'Houston', 'TX', '77001', 'USA', '2023-10-01 12:05:00', '2023-10-01 12:05:00'),
('111 Walnut St', 'Phoenix', 'AZ', '85001', 'USA', '2023-11-10 12:06:00', '2023-11-10 12:06:00'),
('222 Ash St', 'Philadelphia', 'PA', '19101', 'USA', '2023-12-01 12:07:00', '2023-12-01 12:07:00'),
('333 Beech St', 'San Antonio', 'TX', '78201', 'USA', '2023-12-03 12:08:00', '2023-12-03 12:08:00'),
('444 Cherry St', 'Dallas', 'TX', '75201', 'USA', '2023-12-05 12:09:00', '2023-12-05 12:09:00'),
('555 Fir St', 'San Diego', 'CA', '92101', 'USA', '2023-12-07 12:10:00', '2023-12-07 12:10:00'),
('666 Hemlock St', 'San Jose', 'CA', '95101', 'USA', '2023-12-09 12:11:00', '2023-12-09 12:11:00'),
('777 Holly St', 'Austin', 'TX', '73301', 'USA', '2023-12-11 12:12:00', '2023-12-11 12:12:00'),
('888 Ivy St', 'Jacksonville', 'FL', '32099', 'USA', '2023-12-13 12:13:00', '2023-12-13 12:13:00'),
('999 Juniper St', 'Columbus', 'OH', '43085', 'USA', '2023-12-15 12:14:00', '2023-12-15 12:14:00'),
('101 Sycamore St', 'Charlotte', 'NC', '28201', 'USA', '2023-12-17 12:15:00', '2023-12-17 12:15:00'),
('102 Maplewood St', 'Detroit', 'MI', '48201', 'USA', '2023-12-19 12:16:00', '2023-12-19 12:16:00'),
('103 Magnolia St', 'El Paso', 'TX', '79901', 'USA', '2023-12-21 12:17:00', '2023-12-21 12:17:00'),
('104 Linden St', 'Boston', 'MA', '02108', 'USA', '2023-12-23 12:18:00', '2023-12-23 12:18:00'),
('105 Palm St', 'Denver', 'CO', '80201', 'USA', '2023-12-25 12:19:00', '2023-12-25 12:19:00');

INSERT INTO userAddress (userId, addressId) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (5, 5),
(6, 6), (7, 7), (8, 8), (9, 9), (10, 10),
(11, 11), (12, 12), (13, 13), (14, 14), (15, 15),
(16, 16), (17, 17), (18, 18), (19, 19), (20, 20);

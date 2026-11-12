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

-- UUID primary keys stored as text (CHAR(36)), with createdAt ties
CREATE TABLE uuid_items (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    createdAt TIMESTAMP NOT NULL
);

INSERT INTO uuid_items (id, name, createdAt) VALUES
('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'item-1', '2023-01-01 10:00:00'),
('0c3d5e7f-1a2b-4c3d-8e4f-5a6b7c8d9e0f', 'item-2', '2023-01-01 10:00:00'),
('9b2e1c4d-7f3a-4b5c-9d6e-1f2a3b4c5d6e', 'item-3', '2023-01-01 10:00:00'),
('a1b2c3d4-e5f6-4789-8abc-def012345678', 'item-4', '2023-01-01 10:00:00'),
('123e4567-e89b-42d3-a456-426614174000', 'item-5', '2023-02-01 10:00:00'),
('550e8400-e29b-41d4-a716-446655440000', 'item-6', '2023-02-01 10:00:00'),
('c9bf9e57-1685-4c89-bafb-ff5af830be8a', 'item-7', '2023-02-01 10:00:00'),
('00000000-0000-4000-8000-000000000001', 'item-8', '2023-02-01 10:00:00'),
('ffffffff-ffff-4fff-bfff-fffffffffff0', 'item-9', '2023-03-01 10:00:00'),
('7d444840-9dc0-11d1-b245-5ffdce74fad2', 'item-10', '2023-03-01 10:00:00'),
('2c9e8b1a-3d4f-4a5b-8c6d-7e8f9a0b1c2d', 'item-11', '2023-03-01 10:00:00'),
('e0e0e0e0-e0e0-40e0-80e0-e0e0e0e0e0e0', 'item-12', '2023-03-01 10:00:00');

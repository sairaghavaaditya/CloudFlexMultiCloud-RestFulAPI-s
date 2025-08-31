# 🧑‍💻CloudFlex Multi-Cloud RESTful APIs
Author: Sai Raghava Aditya Madabathula
Technologies: Java, Spring Boot, Hibernate/JPA, REST APIs, MySQL, Postman, IntelliJ IDEA

## Overview

CloudFlex Multi-Cloud RESTful APIs is a backend system built using Spring Boot that enables seamless management of files across Google Drive and OneDrive. The system provides RESTful endpoints to retrieve, upload, transfer, and delete files using access tokens for authentication. File metadata is stored in MySQL to allow structured access and future scalability.

This project was developed as part of my Java Developer Intern experience at Infosys Springboard.


## Features

- Retrieve files from Google Drive and OneDrive.
- Upload files to either cloud.
- Transfer files between Google Drive and OneDrive.
- Delete files from cloud storage.
- Store file metadata in MySQL for easy access and management.
- Tested and debugged using Postman.

## Project Structure

CloudFlexMultiCloud-RestFulAPI-s/
│
├── Cloud_Flex_Multi_Cloud-RestfulAPI's/   # Main Spring Boot application code
├── Drives_API's_Configurations.pdf       # Guide for obtaining and storing access tokens
└── README.md                             # Project documentation

## Getting Started

**Prerequisites**
- Java 17+
- Maven
- MySQL Database
- Access tokens for Google Drive and OneDrive (see Drives_API's_Configurations.pdf)
- IDE like IntelliJ IDEA

## Setup

**Clone the repository**:
git clone https://github.com/sairaghavaaditya/CloudFlexMultiCloud-RestFulAPI-s.git


**Configure tokens and database8**:
Follow the instructions in Drives_API's_Configurations.pdf to obtain Google Drive and OneDrive access tokens and configure MySQL database credentials.

**Build and run the application**:

- mvn clean install
- mvn spring-boot:run


**Test APIs**:
Use Postman or any REST client to access endpoints.

**Technologies Used**

- Java 21 – Core backend language
- Spring Boot – REST API framework
- Hibernate/JPA – Database ORM
- MySQL – Metadata storage
- Postman – API testing

- Access Tokens – Google Drive & OneDrive authentication

**Future Plans**

- Integrate a UI layer for multiple users.
- Add support for real-time multi-cloud file synchronization.
- Improve error handling and logging.

## 🎥 Demo  

👉 [Click here to watch the demo video](https://drive.google.com/file/d/1B6HumTGgcbWf4KkUGxVVjhPNobk3hj3X/view?usp=drive_link)  


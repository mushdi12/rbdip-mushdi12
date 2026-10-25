package com.rbdip.bookstore.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Контактные данные клиента после разделения полного имени на два поля.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String address;
    private String phone;

    protected Customer() {
        // Для JPA.
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return lastName == null ? firstName : firstName + " " + lastName;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }
}

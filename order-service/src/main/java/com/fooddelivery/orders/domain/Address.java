package com.fooddelivery.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Delivery address, stored in the orders table itself (an embedded value,
 * not a separate table): an address belongs to exactly one order.
 */
@Embeddable
public class Address {

    @Column(name = "first_name", nullable = false, length = 200)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 200)
    private String lastName;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 200)
    private String street;

    @Column(nullable = false, length = 200)
    private String city;

    @Column(nullable = false, length = 200)
    private String state;

    @Column(nullable = false, length = 200)
    private String zipcode;

    @Column(nullable = false, length = 200)
    private String country;

    @Column(nullable = false, length = 200)
    private String phone;

    protected Address() {
        // for JPA
    }

    public Address(String firstName, String lastName, String email, String street, String city,
                   String state, String zipcode, String country, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipcode = zipcode;
        this.country = country;
        this.phone = phone;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getZipcode() { return zipcode; }
    public String getCountry() { return country; }
    public String getPhone() { return phone; }
}

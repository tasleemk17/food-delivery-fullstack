package com.fooddelivery.orders.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fooddelivery.orders.domain.Address;

/** Delivery address as the frontend sends it. All nine fields are required. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressRequest(String firstName, String lastName, String email, String street,
                             String city, String state, String zipcode, String country, String phone) {

    /** Trimmed, capped at 200 characters (the column size); null if any field is missing or blank. */
    public Address toAddressOrNull() {
        String[] v = {firstName, lastName, email, street, city, state, zipcode, country, phone};
        for (int i = 0; i < v.length; i++) {
            if (v[i] == null || v[i].isBlank()) {
                return null;
            }
            v[i] = v[i].trim();
            if (v[i].length() > 200) {
                v[i] = v[i].substring(0, 200);
            }
        }
        return new Address(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
    }
}

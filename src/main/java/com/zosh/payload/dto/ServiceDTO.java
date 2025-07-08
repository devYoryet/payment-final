package com.zosh.payload.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ServiceDTO {

    private String name;

    private String description;

    private BigDecimal price; // <-- CAMBIO AQUÍ

    private int duration;

    private Long category;

    private String image;
}

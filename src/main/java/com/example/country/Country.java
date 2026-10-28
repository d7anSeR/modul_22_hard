package com.example.country;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record Country(
        Long id,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String capital,
        @NotNull @PositiveOrZero Long population) {
}

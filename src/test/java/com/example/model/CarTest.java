package com.example.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CarTest {

    @Test
    void hashCodeTest() {
        Car car1 = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        Car car2 = new Car("Toyota", "Supra", "2JZ", 280, 1998);
        assertEquals(car1.hashCode(),car2.hashCode());
        assertThat(car1).extracting(Car::hashCode).isEqualTo(car2.hashCode());
    }

    @Test
    void EqualsTest() {
        Car car1 = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        Car car2 = new Car("Toyota", "Supra", "2JZ", 280, 1998);
        assertEquals(car1,car2);
        assertThat(car1).isEqualTo(car2);
    }
    @Test
    void hashSetTest() {
        Car car1 = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        Car car2 = new Car("Toyota", "Supra", "2JZ", 280, 1998);
        Set<Car> set = new HashSet<>();
        set.add(car1);
        set.add(car2);
        assertThat(set).hasSize(1);
    }
    @Test
    void horsePowerDoesNotAffectEquality() {
        Car car1 = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        Car car2 = new Car("Toyota", "Supra", "2JZ", 280, 1998);
        assertThat(car1).isEqualTo(car2);
    }
    @Test
    void equalsHandlesNullEngineCode() {
        Car car1 = new Car("Toyota", "Supra", null, 320, 1998);
        Car car2 = new Car("Toyota", "Supra", null, 280, 1998);
        Car car3 = new Car("Toyota", "Supra", "2JZ", 320, 1998);
        assertThat(car1).isEqualTo(car2);
        assertThat(car1).isNotEqualTo(car3);
        assertThat(car3).isNotEqualTo(car1);
    }
}
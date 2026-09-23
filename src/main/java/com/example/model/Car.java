package com.example.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;
@Entity
@Table(name = "cars")
public class Car{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private String model;
    private String engineCode;
    private int horsePower;
    private int year;
    @Column(precision = 12,scale = 2)
    private BigDecimal price;
    protected Car() {}
    public Car(String brand, String model,String engineCode,int horsePower,int year)
    {
        this.brand= brand;
        this.engineCode= engineCode;
        this.year=year;
        this.model=model;
        this.horsePower=horsePower;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getId()
    {
        return id;
    }
    public String getModel() {
        return model;
    }

    public String getBrand() {
        return brand;
    }

    public int getHorsePower() {
        return horsePower;
    }

    public String getEngineCode() {
        return engineCode;
    }

    public int getYear() {
        return year;
    }
    public BigDecimal getPrice() { return price;}
    public void setPrice(BigDecimal price) { this.price=price; }
    @Override
    public boolean equals(Object o)
    {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        Car car = (Car) o;
        return Objects.equals(engineCode,car.engineCode)
                && Objects.equals(year,car.year)
                && Objects.equals(brand,car.brand)
                && Objects.equals(model,car.model);
    }
    @Override
    public int hashCode()
    {
        return Objects.hash(brand,model,engineCode,year);
    }
    @Override
    public String toString()
    {
        return "Машина {" + brand + " " + model + " " + year + "г " + horsePower + "л.с }";
    }
    private Car(Builder builder)
    {
        this.brand = builder.brand;
        this.engineCode = builder.engineCode;
        this.year = builder.year;
        this.model = builder.model;
        this.horsePower = builder.horsePower;
        this.price = builder.price;
    }
    public static Builder builder()
    {
        return new Builder();
    }
    public static class Builder
    {
        private String brand;
        private String model;
        private String engineCode;
        private int horsePower;
        private int year;
        private BigDecimal price;
        public Builder brand(String brand)
        {
            this.brand=brand;
            return this;
        }
        public Builder price(BigDecimal price)
        {
            this.price=price;
            return this;
        }
        public Builder model(String model)
        {
            this.model=model;
            return this;
        }
        public Builder engineCode(String engineCode)
        {
            this.engineCode=engineCode;
            return this;
        }
        public Builder year(int year)
        {
            this.year=year;
            return this;
        }
        public Builder horsePower(int horsePower)
        {
            this.horsePower=horsePower;
            return this;
        }
        public Car build()
        {
            return new Car(this);
        }
    }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private Owner owner;
    public Owner getOwner()
    {
        return owner;
    }

    public void setHorsePower(int horsePower) {
        this.horsePower = horsePower;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }
}

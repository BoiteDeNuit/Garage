package com.example.model;

import java.util.Objects;

public class Car implements Identifiable {
    private Long id;
    private String brand;
    private String model;
    private String engineCode;
    private int horsePower;
    private int year;
    public Car(String brand, String model,String engineCode,int horsePower,int year)
    {
        this.brand= brand;
        this.engineCode= engineCode;
        this.year=year;
        this.model=model;
        this.horsePower=horsePower;
    }
    @Override
    public void setId(Long id) {
        this.id = id;
    }
    @Override
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

    @Override
    public boolean equals(Object o)
    {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        Car car = (Car) o;
        return year == car.year
                && brand.equals(car.brand)
                && model.equals(car.model)
                && engineCode.equals(car.engineCode);
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
        public Builder brand(String brand)
        {
            this.brand=brand;
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
}

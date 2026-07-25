package com.example.model;

import com.example.dto.CarMapper;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "owners")
public class Owner implements Identifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String city;
    protected Owner() {}
    public Owner(String name,String city)
    {
        this.city = city;
        this.name = name;
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

    public String getCity() {
        return city;
    }

    public String getName() {
        return name;
    }
    public List<Car> getCars() {
        return cars;
    }
    @OneToMany(mappedBy = "owner")
    private List<Car> cars = new ArrayList<>();
    public void addCar(Car car) {
        cars.add(car);
        car.setOwner(this);
    }
    public void removeCar(Car car)
    {
        cars.remove(car);
        car.setOwner(null);
    }

}

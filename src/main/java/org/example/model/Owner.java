package org.example.model;

public class Owner implements Identifiable {
    private Long id;
    private String name;
    private String city;
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
}

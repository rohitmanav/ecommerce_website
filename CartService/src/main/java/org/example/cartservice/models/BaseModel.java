package org.example.cartservice.models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Setter
@Getter
public abstract class BaseModel {
    @Id
    private String id;
    private Date createdAt;
    private Date lastUpdatedAt;

    public BaseModel() {
        this.createdAt = new Date();
        this.lastUpdatedAt = new Date();
    }
}

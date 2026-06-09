package com.example.simple.dto;

import org.byteora.kyra.orm.query.Paging;

public class InheritedPagingUserQuery extends Paging {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

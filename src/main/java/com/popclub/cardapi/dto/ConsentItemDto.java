package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConsentItemDto {

    @JsonProperty("name")
    private String name;

    @JsonProperty("title")
    private String title;

    @JsonProperty("is_parent")
    private boolean isParent;

    @JsonProperty("is_mandatory")
    private boolean isMandatory;

    @JsonProperty("value")
    private boolean value;

    public ConsentItemDto(String name, String title, boolean isParent, boolean isMandatory, boolean value) {
        this.name = name;
        this.title = title;
        this.isParent = isParent;
        this.isMandatory = isMandatory;
        this.value = value;
    }

    public String getName() { return name; }
    public String getTitle() { return title; }
    public boolean isParent() { return isParent; }
    public boolean isMandatory() { return isMandatory; }
    public boolean isValue() { return value; }
}

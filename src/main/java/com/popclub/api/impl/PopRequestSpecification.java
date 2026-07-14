package com.popclub.api.impl;

import com.popclub.api.enums.Authorization;

import java.util.Map;

public class PopRequestSpecification {

    private final String        baseUrl;
    private final Authorization authorization;
    private final String        contentType;
    private final Object        body;
    private final Map<String, String> headers;
    private final boolean       followRedirects;

    private PopRequestSpecification(Builder b) {
        this.baseUrl         = b.baseUrl;
        this.authorization   = b.authorization;
        this.contentType     = b.contentType;
        this.body            = b.body;
        this.headers         = b.headers;
        this.followRedirects = b.followRedirects;
    }

    public String        getBaseUrl()       { return baseUrl; }
    public Authorization getAuthorization() { return authorization; }
    public String        getContentType()   { return contentType; }
    public Object        getBody()          { return body; }
    public Map<String, String> getHeaders() { return headers; }
    public boolean        isFollowRedirects() { return followRedirects; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String        baseUrl;
        private Authorization authorization = Authorization.NONE;
        private String        contentType   = "application/json";
        private Object        body;
        private Map<String, String> headers;
        private boolean       followRedirects = true;

        public Builder baseUrl(String v)             { this.baseUrl = v;       return this; }
        public Builder authorization(Authorization v) { this.authorization = v; return this; }
        public Builder contentType(String v)          { this.contentType = v;   return this; }
        public Builder body(Object v)                 { this.body = v;          return this; }
        public Builder headers(Map<String, String> v) { this.headers = v;       return this; }
        public Builder followRedirects(boolean v)     { this.followRedirects = v; return this; }
        public PopRequestSpecification build()        { return new PopRequestSpecification(this); }
    }
}

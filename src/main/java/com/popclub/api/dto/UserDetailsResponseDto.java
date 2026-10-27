package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetailsResponseDto {

    @JsonProperty("is_success")
    private boolean isSuccess;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private Data data;

    public boolean isSuccess() { return isSuccess; }
    public String getMessage() { return message; }
    public Data getData()      { return data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        @JsonProperty("is_editable")
        private boolean isEditable;

        @JsonProperty("user_details")
        private UserDetails userDetails;

        public boolean isEditable()          { return isEditable; }
        public UserDetails getUserDetails()  { return userDetails; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserDetails {

        @JsonProperty("user_id")      private String userId;
        @JsonProperty("first_name")   private String firstName;
        @JsonProperty("last_name")    private String lastName;
        @JsonProperty("email")        private String email;
        @JsonProperty("pan")          private String pan;
        @JsonProperty("gender")       private String gender;
        @JsonProperty("dob")          private String dob;
        @JsonProperty("occupation")   private String occupation;
        @JsonProperty("marital_status") private String maritalStatus;
        @JsonProperty("pin_code")     private String pinCode;

        public String getUserId()        { return userId; }
        public String getFirstName()     { return firstName; }
        public String getLastName()      { return lastName; }
        public String getEmail()         { return email; }
        public String getPan()           { return pan; }
        public String getGender()        { return gender; }
        public String getDob()           { return dob; }
        public String getOccupation()    { return occupation; }
        public String getMaritalStatus() { return maritalStatus; }
        public String getPinCode()       { return pinCode; }
    }
}

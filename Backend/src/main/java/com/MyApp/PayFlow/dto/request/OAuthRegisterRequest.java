package com.MyApp.PayFlow.dto.request;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OAuthRegisterRequest {
    private String name;
    private String email;
    private String idToken;
}

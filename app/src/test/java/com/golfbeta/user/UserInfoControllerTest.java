package com.golfbeta.user;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserInfoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FirebaseAuth firebaseAuth;

    @Test
    void me_with_valid_token_returns_uid() throws Exception {
        FirebaseToken token = Mockito.mock(FirebaseToken.class);
        Mockito.when(token.getUid()).thenReturn("test-uid");
        Mockito.when(firebaseAuth.verifyIdToken(anyString(), Mockito.eq(true))).thenReturn(token);

        mvc.perform(get("/me").header("Authorization", "Bearer DUMMY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("test-uid"));
    }

    @Test
    void me_without_token_is_401() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }
}

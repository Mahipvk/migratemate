package com.bank.web;

import com.bank.model.Account;
import com.bank.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository repository;

    private Long id;

    @BeforeEach
    void setUp() {
        Account account = new Account();
        account.setOwnerName("Jane Doe");
        account.setBalance(new BigDecimal("100.00"));
        id = repository.save(account).getId();
    }

    @Test
    void getsAccountById() throws Exception {
        mvc.perform(get("/api/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Jane Doe"));
    }

    @Test
    void legacyClientsUseTrailingSlash() throws Exception {
        // Older mobile clients call the URL with a trailing slash; it must keep working.
        mvc.perform(get("/api/accounts/" + id + "/"))
                .andExpect(status().isOk());
    }

    @Test
    void returns404ForUnknownAccount() throws Exception {
        mvc.perform(get("/api/accounts/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void opensAccountWithZeroBalanceByDefault() throws Exception {
        mvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"John Smith\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void rejectsBlankOwnerName() throws Exception {
        mvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"\",\"balance\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ownerName").exists());
    }
}

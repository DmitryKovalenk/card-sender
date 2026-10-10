package com.privatbank.cardsender.client;

import com.privatbank.cardsender.advisors.ApiExceptionHandler;
import com.privatbank.cardsender.controllers.DemoController;
import com.privatbank.cardsender.domain.BusinessException;
import com.privatbank.cardsender.domain.DemoDataResult;
import com.privatbank.cardsender.service.demo.DemoDataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoControllerTests {
    private final DemoDataService service = mock(DemoDataService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void omittedCountDefaultsToOneHundred() throws Exception {
        when(service.generate(100)).thenReturn(new DemoDataResult(100, 100));
        mvc.perform(post("/demo/generate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardOrdersCreated").value(100))
                .andExpect(jsonPath("$.shipmentOrdersCreated").value(100));
        verify(service).generate(100);
    }

    @Test
    void invalidCountIsAnHttpBadRequest() throws Exception {
        when(service.generate(0)).thenThrow(new BusinessException(BusinessException.Kind.INVALID_REQUEST,
                "Count must be between 1 and 1000"));
        mvc.perform(post("/demo/generate").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Count must be between 1 and 1000"));
    }
}

package com.example.country;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CountryControllerTest {
    private static final String URL = "/api/countries";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Country RUSSIA = new Country(null, "Russia", "Moscow", 146_000_000L);

    @Mock
    CountryService service;
    @InjectMocks
    CountryController controller;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }


    private ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(URL).contentType(APPLICATION_JSON).content(body));
    }

    private ResultActions putJson(String id, String body) throws Exception {
        return mvc.perform(put(URL + "/{id}", id).contentType(APPLICATION_JSON).content(body));
    }

    private static String json(Country c) throws Exception {
        return MAPPER.writeValueAsString(c);
    }

    private static String raw(String name, String capital, String population) {
        return "{\"name\":%s,\"capital\":%s,\"population\":%s}".formatted(name, capital, population);
    }

    static Stream<Arguments> invalidBodies() {
        String tooLong = "\"" + "a".repeat(101) + "\"";
        String huge = "\"" + "a".repeat(1_000_000) + "\"";
        return Stream.of(
                Arguments.of("empty object", "{}"),
                Arguments.of("empty body", ""),
                Arguments.of("json null literal", "null"),
                Arguments.of("json array", "[]"),
                Arguments.of("not json", "not json"),
                Arguments.of("truncated json", "{\"name\":\"Rus"),
                Arguments.of("name null", raw("null", "\"Moscow\"", "1")),
                Arguments.of("name empty", raw("\"\"", "\"Moscow\"", "1")),
                Arguments.of("name blank", raw("\"   \"", "\"Moscow\"", "1")),
                Arguments.of("name 101 chars", raw(tooLong, "\"Moscow\"", "1")),
                Arguments.of("name 1M chars", raw(huge, "\"Moscow\"", "1")),
                Arguments.of("name is object", raw("{}", "\"Moscow\"", "1")),
                Arguments.of("capital null", raw("\"Russia\"", "null", "1")),
                Arguments.of("capital blank", raw("\"Russia\"", "\"\\t\\n \"", "1")),
                Arguments.of("capital 101 chars", raw("\"Russia\"", tooLong, "1")),
                Arguments.of("population null", raw("\"Russia\"", "\"Moscow\"", "null")),
                Arguments.of("population -1", raw("\"Russia\"", "\"Moscow\"", "-1")),
                Arguments.of("population Long.MIN", raw("\"Russia\"", "\"Moscow\"", Long.MIN_VALUE + "")),
                Arguments.of("population Long.MAX+1", raw("\"Russia\"", "\"Moscow\"", "9223372036854775808")),
                Arguments.of("population 1e400", raw("\"Russia\"", "\"Moscow\"", "1e400")),
                Arguments.of("population string", raw("\"Russia\"", "\"Moscow\"", "\"abc\"")),
                Arguments.of("population array", raw("\"Russia\"", "\"Moscow\"", "[]")));
    }

    // ---------- POST ----------

    @Test
    void create_valid_returns201AndBody() throws Exception {
        when(service.create(RUSSIA)).thenReturn(new Country(1L, "Russia", "Moscow", 146_000_000L));

        postJson(json(RUSSIA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Russia"))
                .andExpect(jsonPath("$.capital").value("Moscow"))
                .andExpect(jsonPath("$.population").value(146_000_000));
        verify(service).create(RUSSIA);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void create_invalidBody_returns400AndNeverCallsService(String label, String body) throws Exception {
        postJson(body).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void create_noBodyAtAll_returns400() throws Exception {
        mvc.perform(post(URL).contentType(APPLICATION_JSON)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void create_wrongContentType_returns415() throws Exception {
        mvc.perform(post(URL).contentType(TEXT_PLAIN).content(json(RUSSIA)))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @Test
    void create_missingContentType_returns415() throws Exception {
        mvc.perform(post(URL).content(json(RUSSIA))).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @Test
    void create_boundaryValues_accepted() throws Exception {
        Country edge = new Country(null, "n".repeat(100), "c".repeat(100), 0L);
        Country max = new Country(null, "Max", "Max", Long.MAX_VALUE);
        when(service.create(edge)).thenReturn(edge);
        when(service.create(max)).thenReturn(max);

        postJson(json(edge)).andExpect(status().isCreated());
        postJson(json(max)).andExpect(status().isCreated())
                .andExpect(content().string(containsString("9223372036854775807")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"日本", "🇯🇵 Япония", "<script>alert(1)</script>", "'; DROP TABLE countries;--", "a\u0000b"})
    void create_exoticStrings_passedToServiceUnchanged(String name) throws Exception {
        Country c = new Country(null, name, "Capital", 1L);
        when(service.create(c)).thenReturn(c);

        postJson(json(c)).andExpect(status().isCreated());
        verify(service).create(c);
    }

    @Test
    void create_serviceFails_exceptionPropagates() {
        when(service.create(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> postJson(json(RUSSIA))).hasRootCauseInstanceOf(IllegalStateException.class);
    }


    @Test
    void findAll_returnsList() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                new Country(1L, "Russia", "Moscow", 1L), new Country(2L, "France", "Paris", 2L)));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("France"));
    }

    @Test
    void findAll_empty_returnsEmptyArray() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void findAll_tenThousandCountries_ok() throws Exception {
        when(service.findAll()).thenReturn(IntStream.rangeClosed(1, 10_000)
                .mapToObj(i -> new Country((long) i, "C" + i, "K" + i, (long) i)).toList());

        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10_000));
    }

    @Test
    void findAll_serviceFails_exceptionPropagates() {
        when(service.findAll()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> mvc.perform(get(URL))).hasRootCauseInstanceOf(IllegalStateException.class);
    }


    @Test
    void findById_found_returns200() throws Exception {
        when(service.findById(1)).thenReturn(new Country(1L, "Russia", "Moscow", 1L));

        mvc.perform(get(URL + "/{id}", 1)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Russia"));
    }

    @Test
    void findById_notFound_returns404() throws Exception {
        when(service.findById(42)).thenThrow(new CountryNotFoundException(42));

        mvc.perform(get(URL + "/{id}", 42)).andExpect(status().isNotFound());
    }

    @Test
    void findById_longMax_passedToService() throws Exception {
        when(service.findById(Long.MAX_VALUE)).thenThrow(new CountryNotFoundException(Long.MAX_VALUE));

        mvc.perform(get(URL + "/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        verify(service).findById(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-9223372036854775808", "9223372036854775808",
            "99999999999999999999999999", "abc", "1.5", "null", "1e3"})
    void findById_badId_returns400(String id) throws Exception {
        mvc.perform(get(URL + "/{id}", id)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }


    @Test
    void update_valid_returns200() throws Exception {
        when(service.update(5, RUSSIA)).thenReturn(new Country(5L, "Russia", "Moscow", 146_000_000L));

        putJson("5", json(RUSSIA)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        verify(service).update(5, RUSSIA);
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(service.update(7, RUSSIA)).thenThrow(new CountryNotFoundException(7));

        putJson("7", json(RUSSIA)).andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void update_invalidBody_returns400AndNeverCallsService(String label, String body) throws Exception {
        putJson("1", body).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-9223372036854775808", "9223372036854775808",
            "99999999999999999999999999", "abc", "1.5", "null", "1e3"})
    void update_badId_returns400(String id) throws Exception {
        putJson(id, json(RUSSIA)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void update_noBody_returns400() throws Exception {
        mvc.perform(put(URL + "/1").contentType(APPLICATION_JSON)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }


    @Test
    void delete_existing_returns204() throws Exception {
        mvc.perform(delete(URL + "/{id}", 3)).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(3);
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new CountryNotFoundException(9)).when(service).delete(9);

        mvc.perform(delete(URL + "/{id}", 9)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-9223372036854775808", "9223372036854775808",
            "99999999999999999999999999", "abc", "1.5", "null", "1e3"})
    void delete_badId_returns400(String id) throws Exception {
        mvc.perform(delete(URL + "/{id}", id)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }


    @Test
    void unsupportedMethods_return405() throws Exception {
        mvc.perform(patch(URL + "/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(put(URL).contentType(APPLICATION_JSON).content(json(RUSSIA))).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(URL)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(URL + "/1").contentType(APPLICATION_JSON).content(json(RUSSIA)))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }
}

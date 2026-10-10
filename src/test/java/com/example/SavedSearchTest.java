package com.example;

import com.example.model.AppUser;
import com.example.model.Role;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SavedSearchTest extends IntegrationTest {
    private static final String SUPRA = """
            {"name": "Супра до пяти", "criteria": {"brand": "toyota", "model": "supra", "priceTo": 5000000, "fuelType": "PETROL"}}""";

    // В jsonb лежат ровно поля фильтра: без служебных isYearRangeValid и без пустых полей
    @Test
    void searchIsStoredAsCriteriaDocument() throws Exception
    {
        AppUser user = createUser(Role.USER);

        long id = json(save(user, SUPRA).andExpect(status().isCreated())
                .andExpect(jsonPath("$.criteria.brand").value("toyota"))
                .andExpect(jsonPath("$.criteria.fuelType").value("PETROL"))).get("id").asLong();

        JsonNode stored = objectMapper.readTree(jdbcTemplate.queryForObject("select criteria::text from saved_searches where id = ?", String.class, id));
        assertThat(fieldNames(stored)).containsExactlyInAnyOrder("brand", "model", "priceTo", "fuelType");
        mockMvc.perform(get("/api/me/searches").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].criteria.priceTo").value(5000000));
    }

    @Test
    void searchNeedsNameAndAtLeastOneValidFilter() throws Exception
    {
        AppUser user = createUser(Role.USER);

        save(user, """
                {"name": "Всё подряд", "criteria": {"brand": "  "}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Нужен хотя бы один фильтр"));
        save(user, """
                {"name": " ", "criteria": {"brand": "kia"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Название поиска обязательно"));
        save(user, """
                {"name": "Наоборот", "criteria": {"yearFrom": 2010, "yearTo": 2000}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Год «от» больше года «до»"));
        save(user, """
                {"name": "Уголь", "criteria": {"fuelType": "COAL"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Поле fuelType: допустимые значения PETROL, DIESEL, HYBRID, ELECTRIC, GAS"));
    }

    @Test
    void twentyFirstSearchIs409() throws Exception
    {
        AppUser user = createUser(Role.USER);
        for (int i = 0; i < 20; i++)
        {
            save(user, SUPRA).andExpect(status().isCreated());
        }

        save(user, SUPRA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Не больше 20 сохранённых поисков"));
    }

    // Чужой поиск не найти и не удалить: 404, как несуществующий
    @Test
    void onlyOwnerSeesAndDeletesSearch() throws Exception
    {
        AppUser owner = createUser(Role.USER);
        AppUser stranger = createUser(Role.USER);
        long id = json(save(owner, SUPRA)).get("id").asLong();

        mockMvc.perform(get("/api/me/searches").header("Authorization", bearer(stranger))).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(delete("/api/me/searches/{id}", id).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/me/searches/{id}", id).header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/searches").header("Authorization", bearer(owner))).andExpect(jsonPath("$").isEmpty());
    }

    private ResultActions save(AppUser user, String body) throws Exception
    {
        return mockMvc.perform(post("/api/me/searches").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions result) throws Exception
    {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private java.util.List<String> fieldNames(JsonNode node)
    {
        java.util.List<String> names = new java.util.ArrayList<>();
        Iterator<String> it = node.propertyNames().iterator();
        while (it.hasNext())
        {
            names.add(it.next());
        }
        return names;
    }
}

package com.example.repository;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.function.SqlColumn;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

// Функции полнотекстового поиска для Criteria API. Hibernate находит класс через ServiceLoader
// (META-INF/services/org.hibernate.boot.model.FunctionContributor).
// Колонки search_vector в сущности нет (V12): Hibernate читал бы её в каждом SELECT.
// listing_search_vector(l.id) выводит <алиас l>.search_vector — алиас берётся из колонки аргумента,
// так же Hibernate разбирает column() в HQL
public class FullTextFunctions implements FunctionContributor {
    @Override
    public void contributeFunctions(FunctionContributions contributions)
    {
        SqmFunctionRegistry registry = contributions.getFunctionRegistry();
        BasicTypeRegistry types = contributions.getTypeConfiguration().getBasicTypeRegistry();
        registry.register("listing_search_vector", new SqlColumn("search_vector", null));
        // websearch_to_tsquery разбирает строку как поисковик: кавычки — фраза, or, минус — исключить.
        // На кривой ввод не падает, в отличие от to_tsquery. Словарь тот же, что у вектора, иначе стеммы разойдутся
        registry.registerPattern("fts_matches", "(?1 @@ websearch_to_tsquery('russian', ?2))", types.resolve(StandardBasicTypes.BOOLEAN));
        registry.registerPattern("fts_rank", "ts_rank(?1, websearch_to_tsquery('russian', ?2))", types.resolve(StandardBasicTypes.FLOAT));
    }
}

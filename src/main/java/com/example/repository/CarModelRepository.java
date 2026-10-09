package com.example.repository;

import com.example.model.CarModel;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CarModelRepository extends Repository<CarModel, Long> {
    // ON CONFLICT DO NOTHING: пара уже есть (в любом регистре) или её вставила соседняя транзакция.
    // Проверка «есть ли» отдельным SELECT пропустила бы гонку двух публикаций одной модели
    @Modifying
    @Query(value = "insert into car_models (brand, model) values (:brand, :model) on conflict do nothing", nativeQuery = true)
    void insertIfAbsent(@Param("brand") String brand, @Param("model") String model);

    // Порог для <% до конца транзакции (третий аргумент true — как SET LOCAL, в пул соединение уходит без него).
    // По умолчанию 0,6, а опечатка в одну букву даёт около 0,5: «toyta» не нашла бы Toyota.
    // На справочнике из перф-сида верные подсказки от 0,5, лишние до 0,33 («tigan» -> Renault Logan)
    @Query(value = "select set_config('pg_trgm.word_similarity_threshold', '0.4', true)", nativeQuery = true)
    String lowerSimilarityThreshold();

    // word_similarity сравнивает запрос с самым похожим куском строки: «camr» близко к «Toyota Camry»,
    // хотя со всей строкой у него мало общего. <% — то же сравнение с порогом
    // pg_trgm.word_similarity_threshold, под него подходит GIN idx_car_models_trgm
    @Query(value = "select m.* from car_models m where :q <% (m.brand || ' ' || m.model) "
            + "order by word_similarity(:q, m.brand || ' ' || m.model) desc, m.brand, m.model limit :limit", nativeQuery = true)
    List<CarModel> findSimilar(@Param("q") String q, @Param("limit") int limit);

    long count();
}

package com.example.service;

import com.example.dto.ModelSuggestion;
import com.example.repository.CarModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Справочник марок и моделей для подсказок. Пополняется при публикации: туда попадает то, что видели
// покупатели, а не черновики с опечатками. Удалений нет: модель остаётся, даже если объявлений с ней больше нет
@Service
public class CarModelCatalog {
    private final CarModelRepository repository;
    public CarModelCatalog(CarModelRepository repository) { this.repository=repository; }
    // Только внутри транзакции публикации или правки: откатилась она — откатилась и запись в справочник
    @Transactional(propagation = Propagation.MANDATORY)
    public void remember(String brand, String model)
    {
        repository.insertIfAbsent(brand, model);
    }
    // Порог и поиск в одной транзакции: set_config(..., true) живёт до её конца
    @Transactional(readOnly = true)
    public List<ModelSuggestion> suggest(String text, int limit)
    {
        repository.lowerSimilarityThreshold();
        return repository.findSimilar(text, limit).stream()
                .map(model -> new ModelSuggestion(model.getBrand(), model.getModel()))
                .toList();
    }
}

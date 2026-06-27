package com.cookingtodo.repository;

import com.cookingtodo.model.MealPlan;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MealPlanRepository {
    private static final String FILE_PATH = "db.json";
    private final ObjectMapper objectMapper;

    public MealPlanRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Ensure the file exists
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            try {
                objectMapper.writeValue(file, new ArrayList<MealPlan>());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized List<MealPlan> findAll() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(file, new TypeReference<List<MealPlan>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public synchronized Optional<MealPlan> findById(String id) {
        return findAll().stream()
                .filter(plan -> plan.getId().equals(id))
                .findFirst();
    }

    public synchronized MealPlan save(MealPlan mealPlan) {
        List<MealPlan> plans = findAll();
        // Check if updating
        boolean updated = false;
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).getId().equals(mealPlan.getId())) {
                plans.set(i, mealPlan);
                updated = true;
                break;
            }
        }
        if (!updated) {
            plans.add(mealPlan);
        }
        
        try {
            objectMapper.writeValue(new File(FILE_PATH), plans);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mealPlan;
    }

    public synchronized void deleteById(String id) {
        List<MealPlan> plans = findAll();
        boolean removed = plans.removeIf(plan -> plan.getId().equals(id));
        if (removed) {
            try {
                objectMapper.writeValue(new File(FILE_PATH), plans);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
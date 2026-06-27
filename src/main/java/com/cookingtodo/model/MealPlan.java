package com.cookingtodo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealPlan {
    private String id;
    private LocalDateTime createdAt;
    private Preference preferences;
    private Meals meals;
    private List<GroceryItem> groceryList = new ArrayList<>();
    private List<Substitution> substitutions = new ArrayList<>();
    private BudgetAnalysis budgetAnalysis;
    private String aiInsights;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meals {
        private MealDetail breakfast;
        private MealDetail lunch;
        private MealDetail dinner;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealDetail {
        private String name;
        private String description;
        private Macros macros;
        private List<String> steps = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Macros {
        private int calories;
        private int protein; // in grams
        private int carbs;   // in grams
        private int fat;     // in grams
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroceryItem {
        private String item;
        private String quantity;
        private double estimatedCost;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Substitution {
        private String original;
        private String alternative;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetAnalysis {
        private double totalCost;
        private double maxBudget;
        private boolean isFeasible;
        private String statusMessage;
    }
}
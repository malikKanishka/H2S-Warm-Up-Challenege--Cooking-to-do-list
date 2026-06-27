package com.cookingtodo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Preference {
    private String dietaryGoal = "Balanced";
    private int calorieTarget = 2000;
    private double maxBudget = 30.0;
    private List<String> dislikedIngredients = new ArrayList<>();
}
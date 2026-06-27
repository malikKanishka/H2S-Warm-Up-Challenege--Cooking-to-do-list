package com.cookingtodo.service;

import com.cookingtodo.model.MealPlan;
import com.cookingtodo.model.Preference;
import com.cookingtodo.repository.MealPlanRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MealPlanService {

    private final MealPlanRepository repository;
    private final List<Recipe> recipePool = new ArrayList<>();
    private final Map<String, SubstitutionInfo> substitutionDatabase = new HashMap<>();

    public MealPlanService(MealPlanRepository repository) {
        this.repository = repository;
        initializeRecipePool();
        initializeSubstitutionDatabase();
    }

    public List<MealPlan> getAllPlans() {
        return repository.findAll().stream()
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public Optional<MealPlan> getPlanById(String id) {
        return repository.findById(id);
    }

    public void deletePlan(String id) {
        repository.deleteById(id);
    }

    public MealPlan generateMealPlan(Preference prefs) {
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        // 1. Filter out recipes containing disliked ingredients
        List<Recipe> filteredRecipes = recipePool.stream()
                .filter(r -> !containsDislikedIngredients(r, prefs.getDislikedIngredients()))
                .collect(Collectors.toList());

        // Fallback to full pool if filtered list is too small to build a plan
        if (filteredRecipes.stream().filter(r -> r.mealType == MealType.BREAKFAST).count() == 0 ||
            filteredRecipes.stream().filter(r -> r.mealType == MealType.LUNCH).count() == 0 ||
            filteredRecipes.stream().filter(r -> r.mealType == MealType.DINNER).count() == 0) {
            filteredRecipes = recipePool;
        }

        // 2. Select meals based on goal and calorie target
        Recipe breakfast = selectBestRecipe(filteredRecipes, MealType.BREAKFAST, prefs.getDietaryGoal(), prefs.getCalorieTarget() * 0.25);
        Recipe lunch = selectBestRecipe(filteredRecipes, MealType.LUNCH, prefs.getDietaryGoal(), prefs.getCalorieTarget() * 0.35);
        Recipe dinner = selectBestRecipe(filteredRecipes, MealType.DINNER, prefs.getDietaryGoal(), prefs.getCalorieTarget() * 0.40);

        // 3. Map to MealDetail
        MealPlan.Meals meals = new MealPlan.Meals(
                mapToMealDetail(breakfast),
                mapToMealDetail(lunch),
                mapToMealDetail(dinner)
        );

        // 4. Combine groceries and estimate costs
        List<MealPlan.GroceryItem> groceries = new ArrayList<>();
        Map<String, Double> costMap = new HashMap<>();
        Map<String, String> qtyMap = new HashMap<>();

        addIngredientsToGroceries(breakfast, costMap, qtyMap);
        addIngredientsToGroceries(lunch, costMap, qtyMap);
        addIngredientsToGroceries(dinner, costMap, qtyMap);

        for (Map.Entry<String, Double> entry : costMap.entrySet()) {
            groceries.add(new MealPlan.GroceryItem(entry.getKey(), qtyMap.get(entry.getKey()), entry.getValue()));
        }

        double totalCost = groceries.stream().mapToDouble(MealPlan.GroceryItem::getEstimatedCost).sum();

        // 5. Generate substitutions if ingredients match our list
        List<MealPlan.Substitution> substitutions = new ArrayList<>();
        double potentialSavings = 0.0;
        Set<String> ingredientsInPlan = costMap.keySet();
        for (String ing : ingredientsInPlan) {
            String lowercaseIng = ing.toLowerCase();
            for (Map.Entry<String, SubstitutionInfo> subEntry : substitutionDatabase.entrySet()) {
                if (lowercaseIng.contains(subEntry.getKey())) {
                    SubstitutionInfo sub = subEntry.getValue();
                    substitutions.add(new MealPlan.Substitution(ing, sub.alternative, sub.reason));
                    potentialSavings += sub.savings;
                }
            }
        }

        // 6. Budget Feasibility Logic
        boolean isFeasible = totalCost <= prefs.getMaxBudget();
        String statusMessage;
        if (isFeasible) {
            statusMessage = String.format("Perfect! Total estimated cost ($%.2f) is within your daily budget ($%.2f). Plan is fully feasible.", totalCost, prefs.getMaxBudget());
        } else {
            double difference = totalCost - prefs.getMaxBudget();
            if (totalCost - potentialSavings <= prefs.getMaxBudget()) {
                statusMessage = String.format("Warning: Est. cost ($%.2f) exceeds your budget ($%.2f) by $%.2f. FEASIBLE IF YOU APPLY SUBSTITUTIONS. (Potential savings: $%.2f)", totalCost, prefs.getMaxBudget(), difference, potentialSavings);
            } else {
                statusMessage = String.format("Over Budget: Est. cost ($%.2f) exceeds your budget ($%.2f) by $%.2f. Recommend reducing calorie targets or adding cheaper alternatives.", totalCost, prefs.getMaxBudget(), difference);
            }
        }

        // 7. AI Insights
        String aiInsights = generateAiInsights(prefs, meals, totalCost, isFeasible, substitutions);

        // 8. Build MealPlan object
        MealPlan mealPlan = new MealPlan();
        mealPlan.setId(id);
        mealPlan.setCreatedAt(now);
        mealPlan.setPreferences(prefs);
        mealPlan.setMeals(meals);
        mealPlan.setGroceryList(groceries);
        mealPlan.setSubstitutions(substitutions);
        mealPlan.setBudgetAnalysis(new MealPlan.BudgetAnalysis(totalCost, prefs.getMaxBudget(), isFeasible, statusMessage));
        mealPlan.setAiInsights(aiInsights);

        // 9. Save and return
        return repository.save(mealPlan);
    }

    private boolean containsDislikedIngredients(Recipe r, List<String> disliked) {
        if (disliked == null || disliked.isEmpty()) return false;
        for (String dis : disliked) {
            String lowerDis = dis.trim().toLowerCase();
            if (lowerDis.isEmpty()) continue;
            if (r.name.toLowerCase().contains(lowerDis) || r.description.toLowerCase().contains(lowerDis)) {
                return true;
            }
            for (Ingredient ing : r.ingredients) {
                if (ing.name.toLowerCase().contains(lowerDis)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Recipe selectBestRecipe(List<Recipe> pool, MealType mealType, String dietaryGoal, double targetCalories) {
        List<Recipe> typePool = pool.stream()
                .filter(r -> r.mealType == mealType)
                .collect(Collectors.toList());

        // First preference: Match both dietary goal and meal type
        List<Recipe> goalMatches = typePool.stream()
                .filter(r -> r.dietaryGoal.equalsIgnoreCase(dietaryGoal))
                .collect(Collectors.toList());

        List<Recipe> selectionPool = goalMatches.isEmpty() ? typePool : goalMatches;

        // Choose the recipe that is closest to the calorie target
        return selectionPool.stream()
                .min(Comparator.comparingDouble(r -> Math.abs(r.calories - targetCalories)))
                .orElse(recipePool.stream().filter(r -> r.mealType == mealType).findFirst().get());
    }

    private MealPlan.MealDetail mapToMealDetail(Recipe r) {
        return new MealPlan.MealDetail(
                r.name,
                r.description,
                new MealPlan.Macros(r.calories, r.protein, r.carbs, r.fat),
                r.steps
        );
    }

    private void addIngredientsToGroceries(Recipe r, Map<String, Double> costMap, Map<String, String> qtyMap) {
        for (Ingredient ing : r.ingredients) {
            costMap.put(ing.name, costMap.getOrDefault(ing.name, 0.0) + ing.estimatedCost);
            qtyMap.put(ing.name, ing.quantity); // Keep the latest quantity description
        }
    }

    private String generateAiInsights(Preference prefs, MealPlan.Meals meals, double totalCost, boolean isFeasible, List<MealPlan.Substitution> substitutions) {
        StringBuilder insights = new StringBuilder();
        insights.append("Based on your ").append(prefs.getDietaryGoal()).append(" dietary preferences, ");
        insights.append("I have curated a full-day menu targeting ").append(prefs.getCalorieTarget()).append(" calories. ");
        
        int actualCalories = meals.getBreakfast().getMacros().getCalories() +
                             meals.getLunch().getMacros().getCalories() +
                             meals.getDinner().getMacros().getCalories();
        int actualProtein = meals.getBreakfast().getMacros().getProtein() +
                             meals.getLunch().getMacros().getProtein() +
                             meals.getDinner().getMacros().getProtein();

        insights.append(String.format("The current menu provides %d kcal and %dg of protein. ", actualCalories, actualProtein));

        if (!prefs.getDislikedIngredients().isEmpty()) {
            insights.append("Disliked ingredients (")
                    .append(String.join(", ", prefs.getDislikedIngredients()))
                    .append(") were filtered out successfully. ");
        }

        if (isFeasible) {
            insights.append(String.format("This menu costs $%.2f which satisfies your budget of $%.2f. No major substitutions are necessary, but you can consult the swap options for variety.", totalCost, prefs.getMaxBudget()));
        } else {
            insights.append(String.format("The calculated ingredient cost of $%.2f exceeds your budget limit of $%.2f. ", totalCost, prefs.getMaxBudget()));
            if (!substitutions.isEmpty()) {
                insights.append("To make it feasible, I suggest using the substitution recommendations below (e.g. swapping premium proteins like Salmon/Steak for Tofu/Turkey) which will lower the cost significantly while maintaining a high macro content.");
            } else {
                insights.append("Consider adjusting your daily budget threshold or selecting a 'Low Cost' dietary goal to reduce ingredient costs.");
            }
        }

        return insights.toString();
    }

    // --- Static Internal Definitions ---

    private enum MealType {
        BREAKFAST, LUNCH, DINNER
    }

    @Data
    @AllArgsConstructor
    private static class Ingredient {
        private String name;
        private String quantity;
        private double estimatedCost;
    }

    @Data
    @AllArgsConstructor
    private static class Recipe {
        private String name;
        private String description;
        private MealType mealType;
        private String dietaryGoal;
        private int calories;
        private int protein;
        private int carbs;
        private int fat;
        private List<Ingredient> ingredients;
        private List<String> steps;
    }

    @AllArgsConstructor
    private static class SubstitutionInfo {
        private String alternative;
        private String reason;
        private double savings;
    }

    private void initializeSubstitutionDatabase() {
        substitutionDatabase.put("salmon", new SubstitutionInfo("Tofu or Canned Tuna", "Swapping fresh salmon fillet for tofu or canned tuna saves money while retaining protein.", 6.50));
        substitutionDatabase.put("steak", new SubstitutionInfo("Ground Turkey or Lentils", "Sirloin steak can be substituted with ground turkey or brown lentils to fit a strict budget.", 7.50));
        substitutionDatabase.put("chicken breast", new SubstitutionInfo("Chickpeas or Canned Chicken", "Saves money and reduces prep time while maintaining high protein density.", 2.50));
        substitutionDatabase.put("quinoa", new SubstitutionInfo("Brown Rice", "Quinoa is premium; brown rice is an excellent and budget-friendly complex carbohydrate source.", 1.20));
        substitutionDatabase.put("avocado", new SubstitutionInfo("Olive Oil or Hummus", "Olive oil provides healthy fats at a fraction of the cost per serving.", 1.00));
        substitutionDatabase.put("almond butter", new SubstitutionInfo("Peanut Butter", "Peanut butter is much cheaper and offers comparable macro/micro-nutrient counts.", 3.00));
    }

    private void initializeRecipePool() {
        // --- BALANCED GOAL ---
        recipePool.add(new Recipe(
                "Greek Yogurt Berry Parfait",
                "Layered yogurt with blueberries, honey, and a sprinkle of organic granola.",
                MealType.BREAKFAST, "Balanced", 400, 20, 45, 10,
                Arrays.asList(
                        new Ingredient("Greek Yogurt", "200g", 2.00),
                        new Ingredient("Blueberries", "50g", 1.50),
                        new Ingredient("Granola", "30g", 1.00),
                        new Ingredient("Honey", "1 tbsp", 0.50)
                ),
                Arrays.asList("Spoon half the Greek yogurt into a glass.", "Top with half the berries and granola.", "Repeat layers and drizzle with honey.")
        ));
        recipePool.add(new Recipe(
                "Mediterranean Quinoa Salad",
                "Quinoa tossed with fresh cherry tomatoes, cucumber, olives, and feta cheese.",
                MealType.LUNCH, "Balanced", 550, 15, 60, 20,
                Arrays.asList(
                        new Ingredient("Quinoa", "100g", 2.00),
                        new Ingredient("Cherry Tomatoes", "100g", 1.20),
                        new Ingredient("Cucumber", "1 pc", 0.80),
                        new Ingredient("Feta Cheese", "50g", 1.50),
                        new Ingredient("Olive Oil", "1 tbsp", 0.50)
                ),
                Arrays.asList("Cook quinoa according to instructions.", "Chop tomatoes, cucumber, and olives.", "Toss all ingredients together with olive oil and top with crumbled feta.")
        ));
        recipePool.add(new Recipe(
                "Pan-Seared Salmon with Asparagus",
                "Pan-seared salmon fillet served with garlic-roasted asparagus spears.",
                MealType.DINNER, "Balanced", 650, 38, 12, 35,
                Arrays.asList(
                        new Ingredient("Salmon Fillet", "180g", 8.50),
                        new Ingredient("Asparagus", "150g", 3.00),
                        new Ingredient("Garlic & Olive Oil", "Assorted", 1.00)
                ),
                Arrays.asList("Heat olive oil in a pan over medium heat.", "Sear salmon skin-side down for 5 mins, then flip for 4 mins.", "SautÃ© asparagus with minced garlic in the same pan.")
        ));

        // --- HIGH PROTEIN GOAL ---
        recipePool.add(new Recipe(
                "Spinach & Egg White Scramble",
                "Fluffy egg white scramble loaded with fresh baby spinach and turkey bacon.",
                MealType.BREAKFAST, "High Protein", 350, 35, 10, 12,
                Arrays.asList(
                        new Ingredient("Egg Whites", "1 cup", 2.50),
                        new Ingredient("Spinach", "50g", 1.00),
                        new Ingredient("Turkey Bacon", "2 strips", 1.80)
                ),
                Arrays.asList("Cook turkey bacon in a skillet until crisp.", "Add spinach and let it wilt.", "Pour in egg whites, scrambling gently until cooked through.")
        ));
        recipePool.add(new Recipe(
                "Beef & Broccoli Bowl",
                "Stir-fried lean beef slices and broccoli florets tossed in a light soy-garlic sauce.",
                MealType.LUNCH, "High Protein", 600, 48, 25, 20,
                Arrays.asList(
                        new Ingredient("Lean Beef Steak", "200g", 7.50),
                        new Ingredient("Broccoli", "150g", 1.50),
                        new Ingredient("Soy & Garlic Sauce", "Assorted", 0.80),
                        new Ingredient("Brown Rice", "80g", 1.00)
                ),
                Arrays.asList("Cook brown rice.", "Thinly slice beef and stir-fry in a hot skillet for 4 mins.", "Add broccoli and sauce, cover and let broccoli steam for 3 mins.")
        ));
        recipePool.add(new Recipe(
                "Baked Garlic Butter Chicken Breast",
                "Juicy chicken breast baked with garlic butter and served with green beans.",
                MealType.DINNER, "High Protein", 650, 52, 10, 25,
                Arrays.asList(
                        new Ingredient("Chicken Breast", "250g", 5.00),
                        new Ingredient("Green Beans", "150g", 1.50),
                        new Ingredient("Butter & Garlic", "Assorted", 1.00)
                ),
                Arrays.asList("Preheat oven to 400Â°F (200Â°C).", "Rub chicken breast with garlic butter and season.", "Bake chicken and green beans on a sheet pan for 20 minutes.")
        ));

        // --- VEGAN GOAL ---
        recipePool.add(new Recipe(
                "Southwest Tofu Scramble",
                "Crumbled firm tofu sautÃ©ed with turmeric, bell peppers, onions, and black beans.",
                MealType.BREAKFAST, "Vegan", 380, 22, 30, 12,
                Arrays.asList(
                        new Ingredient("Firm Tofu", "200g", 1.80),
                        new Ingredient("Bell Pepper & Onion", "100g", 1.20),
                        new Ingredient("Black Beans", "50g", 0.60),
                        new Ingredient("Turmeric & Spices", "Assorted", 0.40)
                ),
                Arrays.asList("Crumble tofu with a fork.", "SautÃ© chopped peppers and onion in a pan.", "Add tofu, black beans, turmeric, and cook for 6 minutes.")
        ));
        recipePool.add(new Recipe(
                "Chickpea Salad Wrap",
                "Mashed chickpeas mixed with vegan mayo, celery, and greens in a spinach tortilla.",
                MealType.LUNCH, "Vegan", 500, 16, 55, 18,
                Arrays.asList(
                        new Ingredient("Canned Chickpeas", "1 can", 1.20),
                        new Ingredient("Vegan Mayo & Celery", "Assorted", 1.00),
                        new Ingredient("Spinach Tortilla", "1 pc", 0.80),
                        new Ingredient("Mixed Salad Greens", "50g", 0.80)
                ),
                Arrays.asList("Drain chickpeas and mash in a bowl.", "Mix in vegan mayo, chopped celery, salt, and pepper.", "Spread mix on tortilla, top with greens, and wrap tightly.")
        ));
        recipePool.add(new Recipe(
                "Sweet Potato Chickpea Curry",
                "Creamy coconut curry with sweet potatoes, chickpeas, and fresh spinach.",
                MealType.DINNER, "Vegan", 620, 18, 75, 20,
                Arrays.asList(
                        new Ingredient("Sweet Potato", "200g", 1.00),
                        new Ingredient("Canned Chickpeas", "1 can", 1.20),
                        new Ingredient("Coconut Milk", "150ml", 1.50),
                        new Ingredient("Curry Paste & Spinach", "Assorted", 1.20)
                ),
                Arrays.asList("SautÃ© curry paste in a pot, add cubed sweet potatoes.", "Pour in coconut milk and simmer until potatoes are soft (15 mins).", "Add chickpeas and spinach; simmer 5 mins more.")
        ));

        // --- KETO GOAL ---
        recipePool.add(new Recipe(
                "Keto Bacon & Egg Muffins",
                "Baked egg cups wrapped in crispy bacon strips and topped with cheddar cheese.",
                MealType.BREAKFAST, "Keto", 420, 24, 2, 34,
                Arrays.asList(
                        new Ingredient("Eggs", "3 pcs", 1.20),
                        new Ingredient("Bacon Strips", "3 pcs", 2.20),
                        new Ingredient("Cheddar Cheese", "40g", 1.00)
                ),
                Arrays.asList("Preheat oven to 375Â°F (190Â°C).", "Line muffin tins with bacon strips.", "Whisk eggs, pour inside bacon cups, top with cheese, and bake 15 mins.")
        ));
        recipePool.add(new Recipe(
                "Avocado Cobb Salad",
                "Crispy bacon, boiled eggs, grilled chicken, and diced avocado on a bed of greens.",
                MealType.LUNCH, "Keto", 650, 42, 8, 48,
                Arrays.asList(
                        new Ingredient("Chicken Breast", "150g", 3.00),
                        new Ingredient("Avocado", "1 pc", 2.00),
                        new Ingredient("Boiled Egg", "1 pc", 0.40),
                        new Ingredient("Bacon & Blue Cheese Dressing", "Assorted", 1.80)
                ),
                Arrays.asList("Chop cooked chicken, avocado, and boiled egg.", "Arrange over salad greens in columns.", "Drizzle dressing over and serve.")
        ));
        recipePool.add(new Recipe(
                "Garlic Butter Steak Bites with Cauliflower Mash",
                "Tender steak bites seared in butter and served with creamy mashed cauliflower.",
                MealType.DINNER, "Keto", 700, 45, 10, 52,
                Arrays.asList(
                        new Ingredient("Lean Beef Steak", "200g", 7.50),
                        new Ingredient("Cauliflower", "250g", 2.00),
                        new Ingredient("Butter & Heavy Cream", "Assorted", 1.50)
                ),
                Arrays.asList("Steam cauliflower and blend with butter and cream.", "Cut steak into cubes.", "Sear steak in butter in a hot pan for 2 mins per side.")
        ));

        // --- LOW COST GOAL ---
        recipePool.add(new Recipe(
                "Oatmeal with Banana",
                "Simple rolled oats cooked in water, topped with sliced banana and cinnamon.",
                MealType.BREAKFAST, "Low Cost", 320, 8, 60, 5,
                Arrays.asList(
                        new Ingredient("Rolled Oats", "80g", 0.50),
                        new Ingredient("Banana", "1 pc", 0.30),
                        new Ingredient("Cinnamon & Sugar", "Assorted", 0.20)
                ),
                Arrays.asList("Boil oats in water for 5 minutes.", "Stir in sugar and cinnamon.", "Top with sliced banana.")
        ));
        recipePool.add(new Recipe(
                "Simple Tuna Salad",
                "Canned tuna mixed with light mayo and served over simple crackers.",
                MealType.LUNCH, "Low Cost", 450, 26, 30, 15,
                Arrays.asList(
                        new Ingredient("Canned Tuna", "1 can", 1.50),
                        new Ingredient("Mayo", "1 tbsp", 0.30),
                        new Ingredient("Crackers", "1 pack", 0.80)
                ),
                Arrays.asList("Drain canned tuna.", "Mix with mayo in a bowl.", "Serve alongside crackers.")
        ));
        recipePool.add(new Recipe(
                "Classic Egg Fried Rice",
                "Stir-fried day-old white rice with scrambled eggs, green peas, and soy sauce.",
                MealType.DINNER, "Low Cost", 500, 15, 65, 14,
                Arrays.asList(
                        new Ingredient("White Rice", "150g", 0.50),
                        new Ingredient("Eggs", "2 pcs", 0.80),
                        new Ingredient("Frozen Peas", "50g", 0.40),
                        new Ingredient("Soy Oil & Sauce", "Assorted", 0.30)
                ),
                Arrays.asList("Scramble eggs in a wok, then remove.", "SautÃ© rice and peas for 3 minutes.", "Add eggs back and soy sauce, toss well and serve.")
        ));
    }
}
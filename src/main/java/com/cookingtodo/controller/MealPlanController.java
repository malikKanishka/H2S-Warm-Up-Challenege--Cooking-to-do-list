package com.cookingtodo.controller;

import com.cookingtodo.model.MealPlan;
import com.cookingtodo.model.Preference;
import com.cookingtodo.service.MealPlanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class MealPlanController {

    private final MealPlanService service;

    public MealPlanController(MealPlanService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("plans", service.getAllPlans());
        model.addAttribute("preference", new Preference());
        return "index";
    }

    @PostMapping("/generate")
    public String generatePlan(@ModelAttribute Preference preference,
                               @RequestParam(value = "dislikedIngredientsRaw", required = false) String dislikedRaw) {
        if (dislikedRaw != null && !dislikedRaw.trim().isEmpty()) {
            List<String> list = Arrays.stream(dislikedRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            preference.setDislikedIngredients(list);
        }
        MealPlan plan = service.generateMealPlan(preference);
        return "redirect:/plan/" + plan.getId();
    }

    @GetMapping("/plan/{id}")
    public String viewPlan(@PathVariable String id, Model model) {
        Optional<MealPlan> plan = service.getPlanById(id);
        if (plan.isPresent()) {
            model.addAttribute("plan", plan.get());
            return "detail";
        }
        return "redirect:/";
    }

    @PostMapping("/plan/{id}/delete")
    public String deletePlan(@PathVariable String id) {
        service.deletePlan(id);
        return "redirect:/";
    }
}
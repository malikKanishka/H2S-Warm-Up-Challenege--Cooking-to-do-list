# 🍳 CookList — AI-Powered Macro Cooking To-Do List

> Built for **Hack@Skill — Google for Devs Prompt Wars** using **Antigravity**

A smart, AI-driven meal planning web application that generates personalised daily meal plans with macro tracking, grocery lists, budget analysis, and ingredient substitutions — all in a premium, minimalist UI.

---

## 📸 Overview

CookList takes your dietary goals, calorie targets, budget, and food preferences, then generates a complete day's meal plan (Breakfast, Lunch, Dinner) with step-by-step cooking instructions, a smart grocery list, and real-time budget feasibility checks.

---

## ✨ Features

- **AI Meal Generation** — Generates personalised Breakfast, Lunch & Dinner based on dietary profiles (High Protein, Vegan, Keto, Balanced, Low Cost)
- **Macro Tracking** — Calories, Protein, Carbs & Fat breakdown per meal
- **Smart Grocery List** — Auto-generated shopping list with estimated costs per item
- **Budget Feasibility Check** — Instantly flags if your plan exceeds budget and suggests cheaper substitutions
- **Ingredient Substitutions** — Auto-recommends alternatives (e.g. Salmon → Tofu, Chicken → Turkey)
- **Disliked Ingredient Filtering** — Strictly excludes ingredients you don't want
- **Interactive Cooking Steps** — Collapsible step-by-step preparation instructions
- **Checkable Shopping List** — Tick off items as you shop
- **JSON Persistence** — Lightweight flat-file storage, no external database needed

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java Spring Boot 3.x (Maven) |
| Frontend | Thymeleaf + Custom CSS |
| Storage | JSON flat file (`db.json`) |
| Fonts | Inter / Outfit (Google Fonts) |
| Build Tool | Maven |

---

## 📁 Project Structure

```
Prompt wars/
│
├── db.json                          # Local JSON database
├── pom.xml                          # Maven configuration
│
└── src/main/
    ├── java/com/cookingtodo/
    │   ├── CookingTodoApplication.java      # App entry point
    │   ├── controller/
    │   │   └── MealPlanController.java      # Web endpoints
    │   ├── model/
    │   │   ├── MealPlan.java                # Meal plan data model
    │   │   └── Preference.java             # User preferences model
    │   ├── repository/
    │   │   └── MealPlanRepository.java     # JSON CRUD operations
    │   └── service/
    │       └── MealPlanService.java        # AI meal planning engine
    │
    └── resources/
        ├── application.properties
        ├── templates/
        │   ├── index.html                  # Dashboard & input form
        │   └── detail.html                 # Meal plan detail view
        └── static/css/
            └── style.css                   # Premium stylesheet
```

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run Locally

```bash
# Clone the repository
git clone https://github.com/malikKanishka/Cooking-to-do-list.git
cd Cooking-to-do-list

# Build and run
mvn spring-boot:run
```

Then open your browser at:
```
http://localhost:8080
```

---

## 🔄 How It Works

```
User fills preferences → Controller → AI Service → Budget Check → Save to db.json → Render UI
```

1. User submits dietary goal, calorie target, max budget, and disliked ingredients
2. `MealPlanService` selects recipes from a curated pool filtered by profile and preferences
3. Grocery costs are summed and checked against the budget
4. If over budget, cheaper substitutions are automatically recommended
5. The complete plan is saved to `db.json` and rendered in the detail view

---

## 🎨 Design System

Netlify-inspired minimalist dark UI:

| Token | Value |
|---|---|
| Background | `HSL(220, 20%, 10%)` |
| Card Surface | `HSL(220, 20%, 15%)` |
| Brand Accent | `#00AD9F` (teal) |
| Budget OK | `HSL(145, 60%, 45%)` (green) |
| Budget Alert | `HSL(350, 70%, 50%)` (red) |

Visual highlights include glassmorphism card effects, hover animations, collapsible sections, and interactive checkboxes.

---

## 📊 Sample Meal Plan

| Meal | Dish | Calories | Protein |
|---|---|---|---|
| Breakfast | Protein-Packed Scrambled Eggs | 450 kcal | 30g |
| Lunch | Grilled Chicken Quinoa Salad | 650 kcal | 45g |
| Dinner | Baked Salmon with Broccoli | 700 kcal | 40g |
| **Total** | | **1800 kcal** | **115g** |

---

## 🏆 Built At

**Hack@Skill — Google for Devs Prompt Wars**
Developed using **Antigravity** as part of a prompt engineering competition showcasing AI-assisted full-stack application development.

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

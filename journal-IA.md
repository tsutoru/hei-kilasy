## Critère C1 — Pénalité de retard sur les notes

### 1. Exploration et analyse du code existant
- **Demande à l'IA :** Identifier les fichiers du projet `hei-kilasy` responsables de la soumission, du calcul des notes, de l'envoi d'email, du relevé de notes et de l'export CSV, sans modifier l'architecture existante.
- **Proposition de l'IA :** Inspection fichier par fichier de `SubmissionController.java`, `GradeReport.java`, `CsvExport.java`, `Exercise.java`, `schema.sql` et `data.sql`.
- **Décision et justification :** J'ai analysé les fichiers un par un et constaté que `deadline` est stocké en `DATE` (`LocalDate`), `submitted_at` en `TIMESTAMP` (`LocalDateTime`), et que le calcul de la note sur 20 était dupliqué avec des arrondis différents dans les trois classes.

### 2. Implémentation de la règle de pénalité (-10 %/jour entamé, plafond -50 %, minimum 0)
- **Demande à l'IA :** Appliquer la pénalité de retard de manière identique lors de la notation, dans le relevé de notes et dans l'export CSV, sans créer de nouveaux fichiers ni refactoriser l'architecture.
- **Proposition de l'IA :** Ajouter une méthode privée `computeFinalGrade` dans chacun des trois fichiers concernés :
  - Calcul des jours de retard calendaires via `ChronoUnit.DAYS.between(deadline, submittedAt.toLocalDate())` lorsque `submittedAt.toLocalDate().isAfter(deadline)`.
  - Taux de pénalité `Math.min(0.50, 0.10 * lateDays)`.
  - Note pénalisée `Math.max(0.0, rawGrade * (1.0 - penaltyRate))`, arrondie à 1 décimale (`HALF_UP`).
- **Décision et justification :**
  - **`SubmissionController.java`** : intégration de `computeFinalGrade` dans `GET /submissions/{id}` et `PUT /submissions/{id}/grade` (afin que la réponse JSON et l'email envoyé à l'étudiant affichent la note pénalisée).
  - **`GradeReport.java`** : intégration de `computeFinalGrade` (en `BigDecimal` avec `RoundingMode.HALF_UP` à 1 décimale) dans `build(long studentId)` pour que les notes par exercice et la moyenne générale (`average`) reflètent les pénalités.
  - **`CsvExport.java`** : remplacement de l'ancien arrondi `Math.floor` par `computeFinalGrade` avec arrondi `HALF_UP` à 1 décimale (formaté avec une virgule) afin de garantir une valeur strictement identique entre la correction, le relevé et l'export CSV.

### 3. Vérification et tests
- **Commandes exécutées :**
  - `curl http://localhost:8080/exports/grades.csv` : vérification des cas existants (Alice à l'heure : `16,0` ; Bob avec 1 jour de retard : `14,4` ; Clara avec 3 jours de retard : `11,2` ; David avec 8 jours de retard : `8,0` grâce au plafond de -50 %).
  - `curl http://localhost:8080/students/2/transcript` : vérification de la cohérence entre le relevé de notes de Bob (`14.4`, `average: 14.40`) et l'export CSV (`14,4`).
  - `curl -X PUT http://localhost:8080/submissions/7/grade -H "Content-Type: application/json" -d '{"points": 8, "feedback": "Vérification C1"}'` : vérification de la notation d'une soumission en retard (`8/10` soit `16/20` brut $\rightarrow$ `14.4/20` avec 1 jour de retard), ainsi que du mail reçu dans Mailpit (`http://localhost:8025`).
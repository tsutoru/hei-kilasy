package hei.school;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Grades export for the registrar's office (opened in Excel, semicolon
 * separator).
 */
@RestController
public class CsvExport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/exports/grades.csv")
    public ResponseEntity<String> export() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.reference, st.full_name, st.email, st.cohort, e.title, e.deadline, s.submitted_at, s.raw_points, e.max_points, "
                        + "(s.submitted_at::date > e.deadline) AS late "
                        + "FROM submission s JOIN student st ON st.id = s.student_id JOIN exercise e ON e.id = s.exercise_id "
                        + "ORDER BY st.full_name, e.deadline");

        StringBuilder csv = new StringBuilder();
        csv.append("reference;etudiant;email;promotion;exercice;echeance;soumis_le;en_retard;note_sur_20\n");
        for (Map<String, Object> row : rows) {
            String note = "";
            if (row.get("raw_points") != null) {
                double rawPoints = ((BigDecimal) row.get("raw_points")).doubleValue();
                double maxPoints = ((BigDecimal) row.get("max_points")).doubleValue();
                LocalDate deadline = ((Date) row.get("deadline")).toLocalDate();
                LocalDateTime submittedAt = ((Timestamp) row.get("submitted_at")).toLocalDateTime();
                double n = computeFinalGrade(rawPoints, maxPoints, deadline, submittedAt);
                note = String.valueOf(n).replace('.', ',');
            }
            csv.append(row.get("reference")).append(';')
                    .append(row.get("full_name")).append(';')
                    .append(row.get("email")).append(';')
                    .append(row.get("cohort")).append(';')
                    .append(row.get("title")).append(';')
                    .append(row.get("deadline")).append(';')
                    .append(row.get("submitted_at")).append(';')
                    .append(Boolean.TRUE.equals(row.get("late")) ? "oui" : "non").append(';')
                    .append(note).append('\n');
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"notes-kilasy.csv\"")
                .body(csv.toString());
    }

    private double computeFinalGrade(double rawPoints, double maxPoints, LocalDate deadline,
            LocalDateTime submittedAt) {
        long lateDays = 0;
        if (submittedAt.toLocalDate().isAfter(deadline)) {
            lateDays = Math.max(0, ChronoUnit.DAYS.between(deadline, submittedAt.toLocalDate()));
        }
        double penaltyRate = Math.min(0.50, 0.10 * lateDays);
        double rawGrade = (rawPoints / maxPoints) * 20.0;
        double penalizedGrade = Math.max(0.0, rawGrade * (1.0 - penaltyRate));
        return BigDecimal.valueOf(penalizedGrade).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}

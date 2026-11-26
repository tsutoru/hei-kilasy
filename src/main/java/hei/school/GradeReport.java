package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

/**
 * Grade transcript of a student.
 */
@Component
public class GradeReport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> build(long studentId) {
        List<Map<String, Object>> students = jdbcTemplate.queryForList("SELECT * FROM student WHERE id = ?", studentId);
        if (students.isEmpty()) {
            return null;
        }
        Map<String, Object> student = students.get(0);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT e.title, e.deadline, e.max_points, s.id AS submission_id, s.submitted_at, s.raw_points, s.feedback "
                        + "FROM exercise e LEFT JOIN submission s ON s.exercise_id = e.id AND s.student_id = ? "
                        + "ORDER BY e.deadline",
                studentId);

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int graded = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> line = new LinkedHashMap<>();
            LocalDate deadline = ((Date) row.get("deadline")).toLocalDate();
            line.put("exercise", row.get("title"));
            line.put("deadline", deadline);

            if (row.get("submission_id") == null) {
                if (deadline.isBefore(LocalDate.now())) {
                    line.put("status", "NON RENDU");
                } else {
                    line.put("status", "À RENDRE");
                }
                line.put("grade", null);
            } else {
                LocalDateTime submittedAt = ((Timestamp) row.get("submitted_at")).toLocalDateTime();
                line.put("submittedAt", submittedAt);
                line.put("late", submittedAt.toLocalDate().isAfter(deadline));
                if (row.get("raw_points") == null) {
                    line.put("status", "EN ATTENTE DE CORRECTION");
                    line.put("grade", null);
                } else {
                    BigDecimal raw = (BigDecimal) row.get("raw_points");
                    BigDecimal max = (BigDecimal) row.get("max_points");
                    BigDecimal grade = computeFinalGrade(raw, max, deadline, submittedAt);
                    line.put("status", "CORRIGÉ");
                    line.put("grade", grade);
                    line.put("feedback", row.get("feedback"));
                    total = total.add(grade);
                    graded++;
                }
            }
            lines.add(line);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reference", student.get("reference"));
        report.put("student", student.get("full_name"));
        report.put("email", student.get("email"));
        report.put("cohort", student.get("cohort"));
        report.put("exercises", lines);
        report.put("average", graded == 0 ? null : total.divide(new BigDecimal(graded), 2, RoundingMode.HALF_UP));
        return report;
    }

    private BigDecimal computeFinalGrade(BigDecimal raw, BigDecimal max, LocalDate deadline,
            LocalDateTime submittedAt) {
        long lateDays = 0;
        if (submittedAt.toLocalDate().isAfter(deadline)) {
            lateDays = Math.max(0, ChronoUnit.DAYS.between(deadline, submittedAt.toLocalDate()));
        }
        BigDecimal penaltyRate = BigDecimal.valueOf(Math.min(0.50, 0.10 * lateDays));
        BigDecimal rawGrade = raw.multiply(new BigDecimal("20")).divide(max, 6, RoundingMode.HALF_UP);
        BigDecimal penalizedGrade = rawGrade.multiply(BigDecimal.ONE.subtract(penaltyRate));
        if (penalizedGrade.compareTo(BigDecimal.ZERO) < 0) {
            penalizedGrade = BigDecimal.ZERO;
        }
        return penalizedGrade.setScale(1, RoundingMode.HALF_UP);
    }

}
